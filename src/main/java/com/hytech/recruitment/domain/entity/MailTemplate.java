package com.hytech.recruitment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * 信件範本。選取鍵＝（examType × location × jobTitle）。
 */
@Entity
@Table(name = "mail_template")
public class MailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_type", length = 10)
    private String examType;

    @Column(name = "location", length = 10)
    private String location;

    @Column(name = "job_title", length = 60)
    private String jobTitle;

    @Lob
    @Column(name = "body")
    private String body;

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExamType() { return examType; }
    public void setExamType(String examType) { this.examType = examType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
