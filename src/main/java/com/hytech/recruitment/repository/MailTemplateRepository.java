package com.hytech.recruitment.repository;

import com.hytech.recruitment.domain.entity.MailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MailTemplateRepository extends JpaRepository<MailTemplate, Long> {

    /** 範本選取鍵：examType × location × jobTitle。 */
    Optional<MailTemplate> findByExamTypeAndLocationAndJobTitle(String examType, String location, String jobTitle);
}
