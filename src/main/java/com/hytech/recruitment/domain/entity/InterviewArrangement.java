package com.hytech.recruitment.domain.entity;

import com.hytech.recruitment.domain.enums.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 面試安排（信件溝通中後單向帶入，一位一列）。
 * invitationId 唯一，對應邀約主檔。
 * 主管可寫 interviewManager／managerPreferredDates／managerRemark；其餘系統或 HR 維護。
 * HR 於「排面試時間」一併填基本資料（gender／examType／location）與選定單一主管（selectedManager）。
 */
@Entity
@Table(name = "interview_arrangement")
public class InterviewArrangement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invitation_id", nullable = false, unique = true)
    private Long invitationId;

    @Column(name = "candidate_name", length = 50)
    private String candidateName;

    @Column(name = "job_title", length = 60)
    private String jobTitle;

    /** 履歷連結（供主管檢視）。 */
    @Column(name = "resume_link", length = 255)
    private String resumeLink;

    // ---- 基本資料：HR 於面試安排手動填（下拉選單）；原邀約紀錄的欄位移置於此 ----

    /** HR 可寫：性別（下拉 MALE／FEMALE）。寄信 先生/小姐 依此。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    /** HR 可寫：上機考題目／上機試題類型（下拉 基本／AI）。範本選取維度。 */
    @Column(name = "exam_type", length = 10)
    private String examType;

    /** HR 可寫：面試地點（下拉 南港／板橋）。範本選取維度。 */
    @Column(name = "location", length = 10)
    private String location;

    /** 主管可寫：面試主管，可填多人（例：Tom、John；生產環境以 / 或 & 分隔）。 */
    @Column(name = "interview_manager", length = 50)
    private String interviewManager;

    /**
     * HR 可寫：多位面試主管時，HR 媒合成功後選定的「該名主管」。
     * 觸發寄信時只 CC 這一位主管的信箱；單一主管則自動帶入不需填。
     */
    @Column(name = "selected_manager", length = 50)
    private String selectedManager;

    /**
     * 主管可寫：可面試日期，支援換行（多位主管一人一行）。
     * 例（\n 分行）：
     * <pre>
     * Sherry 9/5 下午、9/8 上午
     * John 9/10 下午、9/11 上午
     * </pre>
     */
    @Column(name = "manager_preferred_dates", length = 500)
    private String managerPreferredDates;

    /** HR 可寫：一面時間（改期走此欄，狀態不變；不可帶入過去時間）。 */
    @Column(name = "interview_time")
    private LocalDateTime interviewTime;

    /** 主管可寫：主管備註（例：優先給 Sherry 面試）。HR 不於此表備註。 */
    @Column(name = "manager_remark", length = 255)
    private String managerRemark;

    /** 系統：主管挑選時間，判 7 天反灰用。 */
    @Column(name = "manager_assigned_at")
    private LocalDateTime managerAssignedAt;

    /** 面試準備表連結（供主管／面試官參考）。 */
    @Column(name = "interview_prep_sheet", length = 255)
    private String interviewPrepSheet;

    /** 基本資料表連結（面試前基本資料+問卷；作為 {{面試前資料}} 後備來源）。 */
    @Column(name = "basic_info_sheet", length = 255)
    private String basicInfoSheet;

    /**
     * 面試者回傳資料連結（供主管檢視）。
     * 觸發寄信後面試者收到面試前資料（雲端問卷），填寫完畢由問卷系統自動回寫此連結；
     * 面試者實際回傳前維持 null（主管表顯示「未回傳」）。本期自動回寫尚未實作。
     */
    @Column(name = "candidate_reply_link", length = 255)
    private String candidateReplyLink;

    /** 回傳 Mail System 狀態（例：已處理）；寄信成功後由系統寫入。 */
    @Column(name = "mail_system_status", length = 20)
    private String mailSystemStatus;

    /** 最近一次發信時間（成功或失敗皆記）；供「重新安排」重寄後辨識是哪一次寄的。 */
    @Column(name = "mail_sent_at")
    private LocalDateTime mailSentAt;

    /** 保留，本期不做。 */
    @Column(name = "calendar_id", length = 120)
    private String calendarId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInvitationId() { return invitationId; }
    public void setInvitationId(Long invitationId) { this.invitationId = invitationId; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getResumeLink() { return resumeLink; }
    public void setResumeLink(String resumeLink) { this.resumeLink = resumeLink; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getExamType() { return examType; }
    public void setExamType(String examType) { this.examType = examType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getInterviewManager() { return interviewManager; }
    public void setInterviewManager(String interviewManager) { this.interviewManager = interviewManager; }

    public String getSelectedManager() { return selectedManager; }
    public void setSelectedManager(String selectedManager) { this.selectedManager = selectedManager; }

    public String getManagerPreferredDates() { return managerPreferredDates; }
    public void setManagerPreferredDates(String managerPreferredDates) { this.managerPreferredDates = managerPreferredDates; }

    public LocalDateTime getInterviewTime() { return interviewTime; }
    public void setInterviewTime(LocalDateTime interviewTime) { this.interviewTime = interviewTime; }

    public String getManagerRemark() { return managerRemark; }
    public void setManagerRemark(String managerRemark) { this.managerRemark = managerRemark; }

    public LocalDateTime getManagerAssignedAt() { return managerAssignedAt; }
    public void setManagerAssignedAt(LocalDateTime managerAssignedAt) { this.managerAssignedAt = managerAssignedAt; }

    public String getInterviewPrepSheet() { return interviewPrepSheet; }
    public void setInterviewPrepSheet(String interviewPrepSheet) { this.interviewPrepSheet = interviewPrepSheet; }

    public String getBasicInfoSheet() { return basicInfoSheet; }
    public void setBasicInfoSheet(String basicInfoSheet) { this.basicInfoSheet = basicInfoSheet; }

    public String getCandidateReplyLink() { return candidateReplyLink; }
    public void setCandidateReplyLink(String candidateReplyLink) { this.candidateReplyLink = candidateReplyLink; }

    public String getMailSystemStatus() { return mailSystemStatus; }
    public void setMailSystemStatus(String mailSystemStatus) { this.mailSystemStatus = mailSystemStatus; }

    public LocalDateTime getMailSentAt() { return mailSentAt; }
    public void setMailSentAt(LocalDateTime mailSentAt) { this.mailSentAt = mailSentAt; }

    public String getCalendarId() { return calendarId; }
    public void setCalendarId(String calendarId) { this.calendarId = calendarId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
