package com.hytech.recruitment.service;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.entity.InterviewArrangement;
import com.hytech.recruitment.domain.entity.InvitationRecord;
import com.hytech.recruitment.domain.enums.ContactStatus;
import com.hytech.recruitment.dto.request.CreateInvitationRequest;
import com.hytech.recruitment.dto.request.DeclineRequest;
import com.hytech.recruitment.dto.request.PatchInvitationRequest;
import com.hytech.recruitment.dto.request.ReplyReceivedRequest;
import com.hytech.recruitment.dto.request.ResultRequest;
import com.hytech.recruitment.dto.response.DuplicateCheckResponse;
import com.hytech.recruitment.dto.response.InvitationResponse;
import com.hytech.recruitment.dto.response.PageResponse;
import com.hytech.recruitment.repository.InterviewArrangementRepository;
import com.hytech.recruitment.repository.InvitationRecordRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 邀約主檔領域服務：建立、查詢、欄位級更新、三個月防重複，及所有狀態流轉動作。
 * <p>Spring IoC 一律 Field Injection。</p>
 */
@Service
public class InvitationService {

    /** 三個月＝90 天（防重複基準）。 */
    private static final int DUP_WINDOW_DAYS = 90;

    /** 目前未做登入，建立邀約時以此寫死的預設 HR 帶入「邀請人」（英文名.英文姓氏）。 */
    private static final String DEFAULT_INVITER = "amy.chung";

    /** 新增求職者成功後自動帶入的履歷連結前綴（本期為假資料，後接 invitationId）。 */
    private static final String RESUME_LINK_PREFIX = "https://drive.example.invalid/resume/";

    @Autowired
    private InvitationRecordRepository invitationRepository;
    @Autowired
    private InterviewArrangementRepository arrangementRepository;
    @Autowired
    private StatusMachine statusMachine;
    @Autowired
    private MailService mailService;

    // ---------- CRUD / 查詢 ----------

    @Transactional
    public InvitationResponse create(CreateInvitationRequest req) {
        String phone = normalizePhone(req.phone());
        assertNoDuplicateInvite(req.name(), req.email(), phone);

        InvitationRecord inv = new InvitationRecord();
        inv.setName(req.name());
        inv.setEmail(req.email());
        inv.setPhone(phone);
        inv.setResumeNo(req.resumeNo());
        inv.setChannel(req.channel());
        inv.setJobTitle(req.jobTitle());
        // 邀請人由 HR 於新增求職者時自填；未做登入，留空則帶入預設 HR
        inv.setInviter(req.inviter() != null && !req.inviter().isBlank()
                ? req.inviter().trim() : DEFAULT_INVITER);
        inv.setContactStatus(ContactStatus.EMAIL_CONTACT);

        InvitationRecord saved = invitationRepository.save(inv);
        // 新增成功後自動帶入履歷連結（需先取得自動遞增的 invitationId）；
        // saved 為受管實體，設值後於交易提交時一併 flush。
        saved.setResumeLink(RESUME_LINK_PREFIX + saved.getId());
        return InvitationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> list(ContactStatus status, String channel, String keyword,
                                                 int page, int size) {
        Specification<InvitationRecord> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) {
                ps.add(cb.equal(root.get("contactStatus"), status));
            }
            if (channel != null && !channel.isBlank()) {
                ps.add(cb.equal(root.get("channel"), channel));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                ps.add(cb.or(
                        cb.like(root.get("name"), like),
                        cb.like(root.get("email"), like),
                        cb.like(root.get("phone"), like),
                        cb.like(root.get("jobTitle"), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<InvitationRecord> result = invitationRepository.findAll(spec, pr);
        return PageResponse.of(result, InvitationResponse::from);
    }

    @Transactional(readOnly = true)
    public InvitationResponse get(Long id) {
        return InvitationResponse.from(load(id));
    }

    /** 欄位級更新：只寫非 null 傳入欄位，不整列覆寫。 */
    @Transactional
    public InvitationResponse patch(Long id, PatchInvitationRequest req) {
        InvitationRecord inv = load(id);
        if (req.name() != null) inv.setName(req.name());
        if (req.email() != null) inv.setEmail(req.email());
        if (req.phone() != null) inv.setPhone(normalizePhone(req.phone()));
        if (req.resumeNo() != null) inv.setResumeNo(req.resumeNo());
        if (req.channel() != null) inv.setChannel(req.channel());
        if (req.jobTitle() != null) inv.setJobTitle(req.jobTitle());
        return InvitationResponse.from(inv);
    }

    /** 三個月防重複查詢（不改資料）。有手機以同名＋同手機為鍵，無手機以同名＋同信箱為鍵。 */
    @Transactional(readOnly = true)
    public DuplicateCheckResponse checkDuplicate(String name, String email, String phone) {
        InvitationRecord hit = findRecentInvite(name, email, phone);
        if (hit == null) {
            return new DuplicateCheckResponse(false, null, null);
        }
        return new DuplicateCheckResponse(true, hit.getId(), hit.getInviteSentAt());
    }

    // ---------- 狀態流轉（動作型）----------

    /** 寄一面邀請信：記 inviteSentAt；寄前再跑三個月防重複。 */
    @Transactional
    public InvitationResponse sendInvite(Long id) {
        InvitationRecord inv = load(id);
        if (inv.getContactStatus() != ContactStatus.EMAIL_CONTACT) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "僅 EMAIL_CONTACT 可寄一面邀請，現為 " + inv.getContactStatus());
        }
        // 排除自己後檢查其他重複列
        InvitationRecord dup = findRecentInvite(inv.getName(), inv.getEmail(), inv.getPhone());
        if (dup != null && !dup.getId().equals(inv.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_INVITE_WITHIN_3M)
                    .with("existingInvitationId", dup.getId());
        }
        inv.setInviteSentAt(LocalDateTime.now());
        return InvitationResponse.from(inv);
    }

    /**
     * HR 手動寄二次邀約（透過其他平台寄出後點此記錄，系統不代寄信）：
     * 由 EMAIL_CONTACT 轉為 SECOND_INVITE_PENDING（二次邀約未回覆），並記 secondInviteAt＝寄信時間；
     * 若已在 SECOND_INVITE_PENDING 則視為重寄，僅更新 secondInviteAt。
     */
    @Transactional
    public InvitationResponse secondInvite(Long id) {
        InvitationRecord inv = load(id);
        ContactStatus cur = inv.getContactStatus();
        if (cur != ContactStatus.EMAIL_CONTACT && cur != ContactStatus.SECOND_INVITE_PENDING) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "僅 EMAIL_CONTACT／SECOND_INVITE_PENDING 可寄二次邀約，現為 " + cur);
        }
        inv.setContactStatus(ContactStatus.SECOND_INVITE_PENDING);
        inv.setSecondInviteAt(LocalDateTime.now());
        return InvitationResponse.from(inv);
    }

    /** 人選回覆有意願／主動投遞 → COMMUNICATING；寄推薦信、單向建立面試安排。 */
    @Transactional
    public InvitationResponse replyReceived(Long id, ReplyReceivedRequest req) {
        InvitationRecord inv = load(id);
        statusMachine.assertTransition(inv.getContactStatus(), ContactStatus.COMMUNICATING);
        inv.setContactStatus(ContactStatus.COMMUNICATING);

        String resumeLink = req == null ? null : req.resumeLink();
        ensureArrangement(inv, resumeLink);
        mailService.sendRecommendation(inv, resumeLink);
        return InvitationResponse.from(inv);
    }

    /** HR 至 104 協調 → TIME_CONFIRMING。 */
    @Transactional
    public InvitationResponse confirmTime(Long id) {
        return transition(id, ContactStatus.TIME_CONFIRMING);
    }

    /** 求職者確認 → INTERVIEW_CONFIRMED。 */
    @Transactional
    public InvitationResponse confirmInterview(Long id) {
        return transition(id, ContactStatus.INTERVIEW_CONFIRMED);
    }

    /** 婉拒 → DECLINED，帶原因。 */
    @Transactional
    public InvitationResponse decline(Long id, DeclineRequest req) {
        InvitationRecord inv = load(id);
        statusMachine.assertTransition(inv.getContactStatus(), ContactStatus.DECLINED);
        inv.setContactStatus(ContactStatus.DECLINED);
        inv.setDeclineReason(req.declineReason());
        return InvitationResponse.from(inv);
    }

    /** 面試無故缺席 → BLACKLIST（理由選填，寫入婉拒原因欄）。 */
    @Transactional
    public InvitationResponse blacklist(Long id) {
        return applyResult(id, ContactStatus.BLACKLIST, null);
    }

    /**
     * 設定面試結果／HR 編輯結果（手動調整狀態）。
     * 允許目標：OFFER_EXTENDED（錄取人選）／OFFER_ACCEPTED（接受聘約）／THANKS_LETTER（感謝函）／
     * DECLINED（婉拒）／BLACKLIST（黑名單）。
     */
    @Transactional
    public InvitationResponse result(Long id, ResultRequest req) {
        return applyResult(id, req.result(), req.reason());
    }

    // ---------- helpers ----------

    /**
     * 面試結果的設定與 HR 編輯結果統一入口。
     * <p>來源狀態須為「確認面試」或任一結果狀態（含錄取人選）；目標須為五個結果狀態之一。
     * DECLINED／BLACKLIST 時將（選填）理由寫入婉拒原因欄，其餘結果則清空婉拒原因。
     * 刻意不走 StatusMachine——確認面試後 HR 可於五個結果狀態間自由調整（反悔改判等情境）。</p>
     */
    private InvitationResponse applyResult(Long id, ContactStatus target, String reason) {
        if (target == null || !isResultState(target)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "result 僅能為 錄取人選／接受聘約／感謝函／婉拒／黑名單");
        }
        InvitationRecord inv = load(id);
        ContactStatus cur = inv.getContactStatus();
        if (cur != ContactStatus.INTERVIEW_CONFIRMED && !isResultState(cur)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "僅確認面試後可設定或編輯最終結果，現為 " + cur + "（" + cur.getLabel() + "）");
        }
        inv.setContactStatus(target);
        // 錄取人選（OFFER_EXTENDED）為過渡狀態、非最終結果，故不寫入 result 欄（最終結果留白）；
        // 最終結果僅為 OFFER_ACCEPTED／THANKS_LETTER／DECLINED／BLACKLIST。
        inv.setResult(target == ContactStatus.OFFER_EXTENDED ? null : target);
        if (target == ContactStatus.BLACKLIST || target == ContactStatus.DECLINED) {
            inv.setDeclineReason(blankToNull(reason));
        } else {
            inv.setDeclineReason(null);
        }
        return InvitationResponse.from(inv);
    }

    /** 是否為五個「結果」狀態之一。 */
    private boolean isResultState(ContactStatus s) {
        return s == ContactStatus.OFFER_EXTENDED || s == ContactStatus.OFFER_ACCEPTED
                || s == ContactStatus.THANKS_LETTER || s == ContactStatus.DECLINED
                || s == ContactStatus.BLACKLIST;
    }

    private InvitationResponse transition(Long id, ContactStatus target) {
        InvitationRecord inv = load(id);
        statusMachine.assertTransition(inv.getContactStatus(), target);
        inv.setContactStatus(target);
        return InvitationResponse.from(inv);
    }

    private InvitationRecord load(Long id) {
        return invitationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("邀約紀錄", id));
    }

    private void assertNoDuplicateInvite(String name, String email, String phone) {
        InvitationRecord dup = findRecentInvite(name, email, phone);
        if (dup != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_INVITE_WITHIN_3M)
                    .with("existingInvitationId", dup.getId());
        }
    }

    /**
     * 近 90 天內曾發送一面邀請（inviteSentAt 有值）的最近一列；查無回 null。
     * <p>比對鍵：有手機→同名＋同手機；無手機→同名＋同信箱。不只擋手機號碼，避免同號不同人或填錯誤傷。</p>
     */
    private InvitationRecord findRecentInvite(String name, String email, String phone) {
        String nameKey = blankToNull(name);
        if (nameKey == null) return null;
        LocalDateTime since = LocalDateTime.now().minusDays(DUP_WINDOW_DAYS);

        String phoneKey = blankToNull(phone);
        List<InvitationRecord> hits;
        if (phoneKey != null) {
            hits = invitationRepository.findRecentInvitesByNameAndPhone(nameKey, phoneKey, since);
        } else {
            String emailKey = blankToNull(email);
            if (emailKey == null) return null;
            hits = invitationRepository.findRecentInvitesByNameAndEmail(nameKey, emailKey, since);
        }
        return hits.isEmpty() ? null : hits.get(0);
    }

    /** 單向建立面試安排（已存在則不重建）。 */
    private void ensureArrangement(InvitationRecord inv, String resumeLink) {
        if (arrangementRepository.existsByInvitationId(inv.getId())) {
            return;
        }
        InterviewArrangement arr = new InterviewArrangement();
        arr.setInvitationId(inv.getId());
        arr.setCandidateName(inv.getName());
        arr.setJobTitle(inv.getJobTitle());
        // 未另外提供則沿用新增時自動帶入的履歷連結
        arr.setResumeLink(resumeLink != null && !resumeLink.isBlank()
                ? resumeLink : inv.getResumeLink());
        arrangementRepository.save(arr);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * 手機格式處理：手機為選填。使用者可不輸入「-」，僅驗證去除非數字後須為 09 開頭共 10 碼；
     * 通過後統一格式化為 0966-888-999（4-3-3）儲存與顯示。空白視為未填（回 null）。
     */
    private String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("\\D", "");
        if (!digits.matches("09\\d{8}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手機格式錯誤，須為 09 開頭共 10 碼");
        }
        return digits.substring(0, 4) + "-" + digits.substring(4, 7) + "-" + digits.substring(7);
    }
}
