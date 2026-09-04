package com.hytech.recruitment.service;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.entity.InterviewArrangement;
import com.hytech.recruitment.domain.entity.InvitationRecord;
import com.hytech.recruitment.domain.enums.ContactStatus;
import com.hytech.recruitment.domain.enums.ExamType;
import com.hytech.recruitment.domain.enums.Location;
import com.hytech.recruitment.dto.request.InterviewTimeRequest;
import com.hytech.recruitment.dto.request.TriggerMailRequest;
import com.hytech.recruitment.dto.response.ArrangementResponse;
import com.hytech.recruitment.repository.InterviewArrangementRepository;
import com.hytech.recruitment.repository.InvitationRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 面試安排領域服務。
 * <p>主管欄位白名單（interviewManager／managerPreferredDates／managerRemark）在此把關。</p>
 * <p>HR 的「排面試時間」為單一整併動作：面試者與主管配對成功後，一併填基本資料
 * （gender／examType／location）與選定單一主管（selectedManager），並觸發寄信
 * （CC 該主管、發送範本信）；不動聯繫狀態（改期規則）。</p>
 */
@Service
public class InterviewArrangementService {

    private static final Logger log = LoggerFactory.getLogger(InterviewArrangementService.class);

    /**
     * 主管可寫欄位（白名單）。
     * interviewManager／managerPreferredDates 皆可多值（自由文字，可換行、以、或逗號分隔）；
     * managerRemark 為主管備註（例：優先給 Sherry 面試）。HR 不於此表備註。
     */
    private static final Set<String> MANAGER_WRITABLE =
            Set.of("interviewManager", "managerPreferredDates", "managerRemark");

    @Autowired
    private InterviewArrangementRepository arrangementRepository;
    @Autowired
    private InvitationRecordRepository invitationRepository;
    @Autowired
    private ManagerResolver managerResolver;
    @Autowired
    private MailService mailService;

    @Transactional(readOnly = true)
    public List<ArrangementResponse> list(String manager) {
        List<InterviewArrangement> list = (manager == null || manager.isBlank())
                ? arrangementRepository.findAll()
                : arrangementRepository.findByInterviewManager(manager);
        return list.stream().map(ArrangementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ArrangementResponse get(Long id) {
        return ArrangementResponse.from(load(id));
    }

    /**
     * 主管填寫 interviewManager／managerPreferredDates／managerRemark（皆可多值，日期可換行）。
     * 後端白名單雙重把關：出現任何非白名單欄位 → 403 FIELD_NOT_WRITABLE_BY_ROLE。
     * 首次填入 interviewManager 時記 managerAssignedAt（供 7 天反灰判斷）。
     */
    @Transactional
    public ArrangementResponse updateManagerFields(Long id, Map<String, Object> body) {
        for (String key : body.keySet()) {
            if (!MANAGER_WRITABLE.contains(key)) {
                throw new BusinessException(ErrorCode.FIELD_NOT_WRITABLE_BY_ROLE,
                        "主管不可寫入欄位：" + key
                                + "（僅允許 interviewManager、managerPreferredDates、managerRemark）")
                        .with("field", key);
            }
        }
        InterviewArrangement arr = load(id);
        if (body.containsKey("interviewManager")) {
            Object v = body.get("interviewManager");
            String manager = v == null ? null : v.toString();
            arr.setInterviewManager(manager);
            if (manager != null && !manager.isBlank() && arr.getManagerAssignedAt() == null) {
                arr.setManagerAssignedAt(LocalDateTime.now());
            }
        }
        if (body.containsKey("managerPreferredDates")) {
            Object v = body.get("managerPreferredDates");
            arr.setManagerPreferredDates(v == null ? null : v.toString());
        }
        if (body.containsKey("managerRemark")) {
            Object v = body.get("managerRemark");
            arr.setManagerRemark(v == null ? null : v.toString());
        }
        return ArrangementResponse.from(arr);
    }

    /**
     * HR「排面試時間」單一整併動作（面試者與主管配對成功後）。
     * <ol>
     *   <li>設定一面時間（改期，聯繫狀態不變；不可帶入過去時間，由 DTO @Future 把關）。</li>
     *   <li>一併填基本資料（欄位級更新，只寫非 null 者）：性別、上機考題目 examType（基本／AI）、
     *       面試地點 location（南港／板橋）；examType／location 非法值 → 400 VALIDATION_ERROR。</li>
     *   <li>selectedManager 可留空（不選定）→ 多位主管時寄信一起 CC；有值時須在清單內
     *       （忽略大小寫）→ 記為寄信只 CC 的該位，不在清單 → 422 MANAGER_SELECTION_REQUIRED。</li>
     * </ol>
     * <p>本方法只負責落地資料（自身交易）；寄信另由 {@link #autoTriggerMailIfReady(Long)}
     * 於交易外觸發，使寄信之冪等紀錄不因本交易而回滾。</p>
     */
    @Transactional
    public ArrangementResponse updateInterviewTime(Long id, InterviewTimeRequest req) {
        InterviewArrangement arr = load(id);
        // 欄位級更新：interviewTime 留空（純「儲存」）不覆寫原值；有值時不可為過去（DTO @Future 把關）
        if (req.interviewTime() != null) {
            arr.setInterviewTime(req.interviewTime());
        }

        if (req.gender() != null) {
            arr.setGender(req.gender());
        }
        if (req.examType() != null) {
            if (ExamType.fromLabel(req.examType()) == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "examType 僅能為 基本／AI，收到：" + req.examType()).with("field", "examType");
            }
            arr.setExamType(req.examType());
        }
        if (req.location() != null) {
            if (Location.fromLabel(req.location()) == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "location 僅能為 南港／板橋，收到：" + req.location()).with("field", "location");
            }
            arr.setLocation(req.location());
        }
        if (req.selectedManager() != null) {
            if (req.selectedManager().isBlank()) {
                // 不選定：清除既有選定 → 寄信一起 CC 全部主管
                arr.setSelectedManager(null);
            } else {
                List<String> managers = managerResolver.parseManagers(arr.getInterviewManager());
                if (managers.isEmpty()) {
                    throw new BusinessException(ErrorCode.MANAGER_SELECTION_REQUIRED,
                            "此安排尚未填入面試主管，無法選定");
                }
                String canonical = managerResolver.matchInList(managers, req.selectedManager())
                        .orElseThrow(() -> new BusinessException(ErrorCode.MANAGER_SELECTION_REQUIRED,
                                "selectedManager「" + req.selectedManager() + "」不在面試主管清單內："
                                        + arr.getInterviewManager()).with("selectedManager", req.selectedManager()));
                arr.setSelectedManager(canonical);
            }
        }
        return ArrangementResponse.from(arr);
    }

    /**
     * 「排面試時間」落地後於交易外觸發寄信：僅在已確認面試且基本資料齊備時寄。
     * <ul>
     *   <li>聯繫狀態未達 INTERVIEW_CONFIRMED → 不觸發（略過）。</li>
     *   <li>性別／examType／location 任一缺漏或非法 → 不觸發（略過），待補齊。</li>
     *   <li>齊備 → 委由 {@link MailService#triggerMail}（同步、冪等、CC 選定主管）；
     *       查無範本等仍會以 422 回報，寄信結果落 mail_send_record。</li>
     * </ul>
     */
    public void autoTriggerMailIfReady(Long arrangementId) {
        InterviewArrangement arr = load(arrangementId);
        InvitationRecord inv = invitationRepository.findById(arr.getInvitationId()).orElse(null);
        if (inv == null || inv.getContactStatus() != ContactStatus.INTERVIEW_CONFIRMED) {
            log.info("[SCHEDULE] arrangementId={} 尚未確認面試，排時間後不自動寄信", arrangementId);
            return;
        }
        if (arr.getGender() == null
                || ExamType.fromLabel(arr.getExamType()) == null
                || Location.fromLabel(arr.getLocation()) == null) {
            log.info("[SCHEDULE] arrangementId={} 基本資料未齊，排時間後不自動寄信", arrangementId);
            return;
        }
        mailService.triggerMail(arr.getInvitationId(), new TriggerMailRequest(null));
    }

    private InterviewArrangement load(Long id) {
        return arrangementRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("面試安排", id));
    }
}
