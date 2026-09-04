package com.hytech.recruitment.scheduler;

import com.hytech.recruitment.domain.entity.InterviewArrangement;
import com.hytech.recruitment.domain.entity.InvitationRecord;
import com.hytech.recruitment.domain.enums.ContactStatus;
import com.hytech.recruitment.repository.InterviewArrangementRepository;
import com.hytech.recruitment.repository.InvitationRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 背景排程（每日一次）：
 * ① 二邀偵測：逾 1 日曆日仍 EMAIL_CONTACT → 轉 SECOND_INVITE_PENDING。
 * ② 主管反灰：COMMUNICATING 逾 7 天無 interviewManager → 提示 HR 婉拒。
 * <p>v2 已移除寄信 timer（寄信改同步）。</p>
 */
@Component
public class RecruitmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentScheduler.class);

    @Autowired
    private InvitationRecordRepository invitationRepository;
    @Autowired
    private InterviewArrangementRepository arrangementRepository;

    /** ① 二邀偵測。每日 01:00 執行。 */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void detectSecondInvite() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(1);
        List<InvitationRecord> stale = invitationRepository
                .findByContactStatusAndInviteSentAtBefore(ContactStatus.EMAIL_CONTACT, threshold);
        for (InvitationRecord inv : stale) {
            inv.setContactStatus(ContactStatus.SECOND_INVITE_PENDING);
            log.info("[SCHED] 二邀偵測：invitationId={} 逾 1 日未更新 → SECOND_INVITE_PENDING", inv.getId());
        }
        if (!stale.isEmpty()) {
            log.info("[SCHED] 二邀偵測完成，共轉 {} 筆", stale.size());
        }
    }

    /** ② 主管反灰提示。每日 01:10 執行。 */
    @Scheduled(cron = "0 10 1 * * *")
    @Transactional(readOnly = true)
    public void detectManagerGrayOut() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<InvitationRecord> communicating =
                invitationRepository.findByContactStatus(ContactStatus.COMMUNICATING);
        for (InvitationRecord inv : communicating) {
            InterviewArrangement arr = arrangementRepository.findByInvitationId(inv.getId()).orElse(null);
            boolean noManager = arr == null
                    || arr.getInterviewManager() == null
                    || arr.getInterviewManager().isBlank();
            LocalDateTime since = arr != null && arr.getCreatedAt() != null
                    ? arr.getCreatedAt() : inv.getInviteSentAt();
            if (noManager && since != null && since.isBefore(threshold)) {
                log.warn("[SCHED] 主管反灰：invitationId={}（{}）逾 7 天未挑選，建議 HR 婉拒",
                        inv.getId(), inv.getName());
            }
        }
    }
}
