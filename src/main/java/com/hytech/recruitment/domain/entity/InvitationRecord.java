package com.hytech.recruitment.domain.entity;

import com.hytech.recruitment.domain.enums.ContactStatus;
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
 * 邀約紀錄（主檔 · 單一真實來源）。
 * id 即 invitationId，自動遞增，貫穿三資源。
 * <p>exam_type／location 於 DB 以中文字面儲存（範本選取鍵），故以 String 對應。</p>
 */
@Entity
@Table(name = "invitation_record")
public class InvitationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "email", nullable = false, length = 120)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    /** 有值＝104 來源；無值＝其他管道。 */
    @Column(name = "resume_no", length = 30)
    private String resumeNo;

    /**
     * 履歷連結：新增求職者成功後由系統自動帶入（本期以假資料
     * https://drive.example.invalid/resume/{invitationId} 產生）。
     */
    @Column(name = "resume_link", length = 255)
    private String resumeLink;

    @Column(name = "channel", length = 40)
    private String channel;

    @Column(name = "job_title", length = 60)
    private String jobTitle;

    /**
     * 邀請人（記錄發出此邀約的 HR），格式：英文名.英文姓氏（例：amy.chung）。
     * 目前未做登入，建立時以寫死的預設 HR 帶入。
     */
    @Column(name = "inviter", length = 60)
    private String inviter;

    // 性別／上機試題類型／面試地點：已移置面試安排（HR 於面試安排手動填），本表不再保留。

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_status", nullable = false, length = 30)
    private ContactStatus contactStatus;

    /** 一面邀請寄出時間（三個月防重複的基準）。 */
    @Column(name = "invite_sent_at")
    private LocalDateTime inviteSentAt;

    @Column(name = "second_invite_at")
    private LocalDateTime secondInviteAt;

    @Column(name = "decline_reason", length = 60)
    private String declineReason;

    /** 最終結果（OFFER_ACCEPTED／THANKS_LETTER／DECLINED／BLACKLIST）。OFFER_EXTENDED 為過渡狀態，不寫此欄。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 30)
    private ContactStatus result;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getResumeNo() { return resumeNo; }
    public void setResumeNo(String resumeNo) { this.resumeNo = resumeNo; }

    public String getResumeLink() { return resumeLink; }
    public void setResumeLink(String resumeLink) { this.resumeLink = resumeLink; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getInviter() { return inviter; }
    public void setInviter(String inviter) { this.inviter = inviter; }

    public ContactStatus getContactStatus() { return contactStatus; }
    public void setContactStatus(ContactStatus contactStatus) { this.contactStatus = contactStatus; }

    public LocalDateTime getInviteSentAt() { return inviteSentAt; }
    public void setInviteSentAt(LocalDateTime inviteSentAt) { this.inviteSentAt = inviteSentAt; }

    public LocalDateTime getSecondInviteAt() { return secondInviteAt; }
    public void setSecondInviteAt(LocalDateTime secondInviteAt) { this.secondInviteAt = secondInviteAt; }

    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }

    public ContactStatus getResult() { return result; }
    public void setResult(ContactStatus result) { this.result = result; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
