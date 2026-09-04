package com.hytech.recruitment.service;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.entity.InterviewArrangement;
import com.hytech.recruitment.domain.entity.InvitationRecord;
import com.hytech.recruitment.domain.entity.MailSendRecord;
import com.hytech.recruitment.domain.entity.MailTemplate;
import com.hytech.recruitment.domain.enums.ContactStatus;
import com.hytech.recruitment.domain.enums.ExamType;
import com.hytech.recruitment.domain.enums.Gender;
import com.hytech.recruitment.domain.enums.Location;
import com.hytech.recruitment.domain.enums.MailSendStatus;
import com.hytech.recruitment.dto.request.TriggerMailRequest;
import com.hytech.recruitment.dto.response.MailRecordResponse;
import com.hytech.recruitment.dto.response.MailTemplateResponse;
import com.hytech.recruitment.repository.InterviewArrangementRepository;
import com.hytech.recruitment.repository.InvitationRecordRepository;
import com.hytech.recruitment.repository.MailSendRecordRepository;
import com.hytech.recruitment.repository.MailTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 寄信領域服務：範本選取、變數合併、同步寄送、冪等守衛與結果寫回。
 * <p>Spring IoC 一律 Field Injection。</p>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    /** 寄信成功後回寫面試安排的 Mail System 狀態（HR 表顯示「成功」）。 */
    private static final String MAIL_OK = "已處理";
    /** 寄信未成功時回寫面試安排（HR 表顯示「失敗」，供 HR 人工另外寄送之提醒）。 */
    private static final String MAIL_FAILED = "寄送失敗";

    @Autowired
    private InvitationRecordRepository invitationRepository;
    @Autowired
    private InterviewArrangementRepository arrangementRepository;
    @Autowired
    private MailTemplateRepository templateRepository;
    @Autowired
    private MailSendRecordRepository sendRecordRepository;
    @Autowired
    private MailSender mailSender;
    @Autowired
    private ManagerResolver managerResolver;

    /**
     * 觸發面試通知信（同步 + 冪等）。
     * 流程：先查是否已 SUCCESS（略過）→ 校驗狀態 → 帶入寄信參數（欄位級）
     * → 選範本（查無標 FAILED 並丟 422）→ 合併變數、寄信、CC 主管 → 寫回結果。
     * <p>刻意「不」包一個大交易：查無範本時要保留 FAILED 紀錄並同時回 422，
     * 若在同一交易內拋例外會連同 FAILED 寫入一起回滾。改以各步驟自身交易落地。</p>
     */
    public MailRecordResponse triggerMail(Long invitationId, TriggerMailRequest req) {
        InvitationRecord inv = invitationRepository.findById(invitationId)
                .orElseThrow(() -> BusinessException.notFound("邀約紀錄", invitationId));

        // 冪等守衛：已成功即略過，不重寄
        Optional<MailSendRecord> existing = sendRecordRepository.findByInvitationId(invitationId);
        if (existing.isPresent() && existing.get().getStatus() == MailSendStatus.SUCCESS) {
            log.info("[MAIL] invitationId={} 已成功寄送，略過重寄", invitationId);
            return MailRecordResponse.from(existing.get());
        }

        // 面試安排：基本資料（性別／上機試題類型／面試地點）與職稱一律以「面試安排」為準
        InterviewArrangement arr = arrangementRepository.findByInvitationId(invitationId).orElse(null);
        LocalDateTime interviewTime = arr != null ? arr.getInterviewTime() : null;
        String examType = arr != null ? arr.getExamType() : null;
        String location = arr != null ? arr.getLocation() : null;
        String jobTitle = arr != null ? arr.getJobTitle() : inv.getJobTitle();

        MailSendRecord record = existing.orElseGet(MailSendRecord::new);
        record.setInvitationId(invitationId);
        record.setEmail(inv.getEmail());
        record.setGender(arr != null ? arr.getGender() : null);
        record.setJobTitle(jobTitle);
        record.setExamType(examType);
        record.setLocation(location);
        record.setFormLink(req.formLink());
        record.setInterviewTime(interviewTime);
        record.setSentAt(LocalDateTime.now());

        // 狀態校驗：須先確認面試
        if (inv.getContactStatus() != ContactStatus.INTERVIEW_CONFIRMED) {
            markArrangementMail(arr, MAIL_FAILED);
            return fail(record, "尚未確認面試，暫不寄送");
        }

        // 寄信必填欄位校驗（皆由 HR 於面試安排填）：性別、上機試題類型、面試地點
        List<String> missing = new ArrayList<>();
        if (arr == null) {
            missing.add("面試安排(基本資料尚未建立)");
        } else {
            if (arr.getGender() == null) missing.add("性別(gender)");
            if (ExamType.fromLabel(examType) == null) missing.add("上機試題類型(examType 基本/AI)");
            if (Location.fromLabel(location) == null) missing.add("面試地點(location 南港/板橋)");
        }
        if (!missing.isEmpty()) {
            String msg = "寄信必填欄位缺漏：" + String.join("、", missing);
            markArrangementMail(arr, MAIL_FAILED);
            fail(record, msg);
            throw new BusinessException(ErrorCode.MAIL_REQUIRED_FIELDS_MISSING, msg).with("missing", missing);
        }

        // CC 面試主管：單一自動帶入；多位由 HR 於面試安排選擇——不選定則全部一起 CC，選定則只 CC 該位
        List<String> ccManagerNames;
        try {
            ccManagerNames = managerResolver.chooseCcManagers(arr);
        } catch (BusinessException e) {
            markArrangementMail(arr, MAIL_FAILED);
            fail(record, e.getMessage());
            throw e;
        }
        List<String> ccManagerEmails = managerResolver.resolveEmails(ccManagerNames);
        String ccManagerEmail = ccManagerEmails.isEmpty() ? null : String.join(",", ccManagerEmails);
        record.setCcManagerEmail(ccManagerEmail);

        // 範本選取：examType × location × jobTitle（皆取自面試安排）
        MailTemplate template = templateRepository
                .findByExamTypeAndLocationAndJobTitle(examType, location, jobTitle)
                .orElse(null);
        if (template == null) {
            markArrangementMail(arr, MAIL_FAILED);
            fail(record, "查無信件範本（TEMPLATE_NOT_FOUND）");
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                    "查無信件範本：examType=" + examType
                            + ", location=" + location + ", jobTitle=" + jobTitle);
        }

        // {{面試前資料}} 來源：優先 request.formLink，否則帶入基本資料表連結
        String preInfoLink = (req.formLink() != null && !req.formLink().isBlank())
                ? req.formLink()
                : arr.getBasicInfoSheet();

        // 合併變數、寄送、CC 主管（先生/小姐 依面試安排之性別）
        String body = mergeTemplate(template.getBody(), inv.getName(), arr.getGender(), interviewTime, preInfoLink);
        boolean ok = mailSender.send(inv.getEmail(), ccManagerEmail, "面試通知", body);
        if (!ok) {
            markArrangementMail(arr, MAIL_FAILED);
            return fail(record, "Email 退信，待更正重寄");
        }

        record.setStatus(MailSendStatus.SUCCESS);
        record.setErrorMessage(null);
        MailSendRecord saved = sendRecordRepository.save(record);
        // 回寫面試安排：Mail System 已處理（HR 表「發信狀態」顯示成功）。
        // 註：面試者回傳資料連結（candidateReplyLink）於面試者實際回傳後才帶入，
        // 不因寄信成功而預先填入，故主管表在面試者回傳前維持「未回傳」。
        markArrangementMail(arr, MAIL_OK);
        return MailRecordResponse.from(saved);
    }

    /**
     * 回寫面試安排的 Mail System 狀態（成功／失敗）＋本次發信時間；arr 為 null 則略過。
     * 發信時間無論成功或失敗皆更新，供「重新安排」重寄後辨識是哪一次寄的。
     */
    private void markArrangementMail(InterviewArrangement arr, String status) {
        if (arr != null) {
            arr.setMailSystemStatus(status);
            arr.setMailSentAt(LocalDateTime.now());
            arrangementRepository.save(arr);
        }
    }

    /** 依 invitationId 寄推薦信給主管（供獨立端點呼叫）。 */
    @Transactional
    public void sendRecommendation(Long invitationId) {
        InvitationRecord inv = invitationRepository.findById(invitationId)
                .orElseThrow(() -> BusinessException.notFound("邀約紀錄", invitationId));
        String resumeLink = arrangementRepository.findByInvitationId(invitationId)
                .map(InterviewArrangement::getResumeLink)
                .orElse(null);
        sendRecommendation(inv, resumeLink);
    }

    /** 寄人才履歷推薦信給主管（進入 COMMUNICATING 時）。 */
    public void sendRecommendation(InvitationRecord inv, String resumeLink) {
        String body = "<p>推薦人選：" + inv.getName() + "（" + inv.getJobTitle() + "）</p>"
                + "<p>履歷連結：" + (resumeLink == null ? "（無）" : resumeLink) + "</p>"
                + "<p>請主管於面試安排中填寫「面試主管」與「可面試日期」。</p>";
        mailSender.send("managers@hy-tech.com.tw", null, "人才履歷推薦：" + inv.getName(), body);
    }

    @Transactional(readOnly = true)
    public MailRecordResponse getByInvitationId(Long invitationId) {
        return sendRecordRepository.findByInvitationId(invitationId)
                .map(MailRecordResponse::from)
                .orElseThrow(() -> BusinessException.notFound("寄信結果", invitationId));
    }

    @Transactional(readOnly = true)
    public List<MailTemplateResponse> listTemplates(String examType, String location, String jobTitle) {
        return templateRepository.findAll().stream()
                .filter(t -> examType == null || examType.equals(t.getExamType()))
                .filter(t -> location == null || location.equals(t.getLocation()))
                .filter(t -> jobTitle == null || jobTitle.equals(t.getJobTitle()))
                .map(MailTemplateResponse::from)
                .toList();
    }

    // ---- helpers ----

    private MailRecordResponse fail(MailSendRecord record, String message) {
        record.setStatus(MailSendStatus.FAILED);
        record.setErrorMessage(message);
        MailSendRecord saved = sendRecordRepository.save(record);
        return MailRecordResponse.from(saved);
    }

    /** 合併範本變數：{{姓氏}}{{先生/小姐}}{{面試時間}}{{面試前資料}}（性別取自面試安排）。 */
    private String mergeTemplate(String body, String candidateName, Gender gender,
                                 LocalDateTime interviewTime, String formLink) {
        String surname = candidateName == null || candidateName.isEmpty()
                ? "" : candidateName.substring(0, 1);
        String honorific = gender == Gender.FEMALE ? "小姐" : "先生";
        String time = interviewTime == null ? "（另行通知）" : interviewTime.format(TIME_FMT);
        String preInfo = formLink == null || formLink.isBlank() ? "（請見後續信件）" : formLink;
        return body
                .replace("{{姓氏}}", surname)
                .replace("{{先生/小姐}}", honorific)
                .replace("{{面試時間}}", time)
                .replace("{{面試前資料}}", preInfo);
    }
}
