package com.hytech.recruitment.repository;

import com.hytech.recruitment.domain.entity.MailSendRecord;
import com.hytech.recruitment.domain.enums.MailSendStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MailSendRecordRepository extends JpaRepository<MailSendRecord, Long> {

    Optional<MailSendRecord> findByInvitationId(Long invitationId);

    /** 冪等守衛：此邀約是否已有成功寄送紀錄。 */
    boolean existsByInvitationIdAndStatus(Long invitationId, MailSendStatus status);
}
