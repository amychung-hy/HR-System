package com.hytech.recruitment.repository;

import com.hytech.recruitment.domain.entity.InterviewArrangement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewArrangementRepository extends JpaRepository<InterviewArrangement, Long> {

    Optional<InterviewArrangement> findByInvitationId(Long invitationId);

    boolean existsByInvitationId(Long invitationId);

    List<InterviewArrangement> findByInterviewManager(String interviewManager);
}
