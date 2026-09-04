package com.hytech.recruitment.domain.entity;

import com.hytech.recruitment.domain.enums.Gender;
import com.hytech.recruitment.domain.enums.MailSendStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 寄信結果（原「設定與紀錄」）。
 * invitationId UNIQUE 即冪等鍵：一個邀約至多一筆，取代佇列。
 */
@Entity
@Table(name = "mail_send_record")
public class MailSendRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invitation_id", nullable = false, unique = true)
    private Long invitationId;

    @Column(name = "email", length = 120)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "job_title", length = 60)
    private String jobTitle;

    @Column(name = "exam_type", length = 10)
    private String examType;

    @Column(name = "location", length = 10)
    private String location;

    @Column(name = "interview_time")
    private LocalDateTime interviewTime;

    @Column(name = "form_link", length = 255)
    private String formLink;

    /** CC 面試主管（多位以逗號分隔）。 */
    @Column(name = "cc_manager_email", length = 512)
    private String ccManagerEmail;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private MailSendStatus status;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInvitationId() { return invitationId; }
    public void setInvitationId(Long invitationId) { this.invitationId = invitationId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getExamType() { return examType; }
    public void setExamType(String examType) { this.examType = examType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDateTime getInterviewTime() { return interviewTime; }
    public void setInterviewTime(LocalDateTime interviewTime) { this.interviewTime = interviewTime; }

    public String getFormLink() { return formLink; }
    public void setFormLink(String formLink) { this.formLink = formLink; }

    public String getCcManagerEmail() { return ccManagerEmail; }
    public void setCcManagerEmail(String ccManagerEmail) { this.ccManagerEmail = ccManagerEmail; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public MailSendStatus getStatus() { return status; }
    public void setStatus(MailSendStatus status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
