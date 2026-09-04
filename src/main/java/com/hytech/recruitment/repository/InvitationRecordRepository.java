package com.hytech.recruitment.repository;

import com.hytech.recruitment.domain.entity.InvitationRecord;
import com.hytech.recruitment.domain.enums.ContactStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InvitationRecordRepository
        extends JpaRepository<InvitationRecord, Long>, JpaSpecificationExecutor<InvitationRecord> {

    /**
     * 三個月防重複（有手機）：同名＋同手機，且在 since 之後曾發送一面邀請（inviteSentAt 有值）者。
     * 只擋手機號碼易誤傷（同號不同人／填錯），故一律以「同名＋同手機」為鍵。
     */
    @Query("""
            select r from InvitationRecord r
            where r.inviteSentAt is not null
              and r.inviteSentAt >= :since
              and r.name = :name
              and r.phone = :phone
            order by r.inviteSentAt desc
            """)
    List<InvitationRecord> findRecentInvitesByNameAndPhone(@Param("name") String name,
                                                           @Param("phone") String phone,
                                                           @Param("since") LocalDateTime since);

    /**
     * 三個月防重複（無手機）：改以同名＋同信箱為鍵，同樣需在 since 之後曾發送一面邀請者。
     */
    @Query("""
            select r from InvitationRecord r
            where r.inviteSentAt is not null
              and r.inviteSentAt >= :since
              and r.name = :name
              and r.email = :email
            order by r.inviteSentAt desc
            """)
    List<InvitationRecord> findRecentInvitesByNameAndEmail(@Param("name") String name,
                                                           @Param("email") String email,
                                                           @Param("since") LocalDateTime since);

    /** 背景排程：逾期未更新、仍為指定狀態者（用於二邀偵測）。 */
    List<InvitationRecord> findByContactStatusAndInviteSentAtBefore(ContactStatus status, LocalDateTime before);

    List<InvitationRecord> findByContactStatus(ContactStatus status);
}
