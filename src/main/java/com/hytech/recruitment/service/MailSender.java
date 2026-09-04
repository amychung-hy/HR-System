package com.hytech.recruitment.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 寄信器：以 Gmail SMTP（應用程式密碼）真實寄送 HTML 信件。
 * <p>設計為「可退化」：未設定 {@code MAIL_PASSWORD}（或 {@code app.mail.enabled=false}）時，
 * 僅將信件內容寫入日誌而不真的連線寄送，讓開發／示範環境不需憑證也能跑。</p>
 * <p>回傳 {@code false} 代表寄送失敗（退信），由 {@link MailService} 落 FAILED 紀錄並可重寄。</p>
 */
@Component("recruitmentMailSender")
public class MailSender {

    private static final Logger log = LoggerFactory.getLogger(MailSender.class);

    /** 有設定 spring.mail.host 時 Spring 會自動裝配此 bean；理論上恆存在，仍以 required=false 防呆。 */
    @Autowired(required = false)
    private JavaMailSender javaMailSender;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.enabled:true}")
    private boolean enabled;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    /**
     * 寄送 HTML 信。
     *
     * @param to       收件者（求職者信箱）
     * @param cc       副本（面試主管信箱，可為 null；多位以半形逗號分隔）
     * @param subject  主旨
     * @param htmlBody HTML 內文
     * @return 是否成功（退化為 log-only 時恆為 true）
     */
    public boolean send(String to, String cc, String subject, String htmlBody) {
        String sender = resolveFrom();
        if (!canSend()) {
            log.info("[MAIL:LOG-ONLY] 未設定寄信憑證或已停用，僅記錄不寄送。from={} to={} cc={} subject={}\n{}",
                    sender, to, cc, subject, htmlBody);
            return true;
        }
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(sender);
            helper.setTo(to);
            if (cc != null && !cc.isBlank()) {
                helper.setCc(cc.split("\\s*,\\s*"));
            }
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            javaMailSender.send(message);
            log.info("[MAIL:SENT] from={} to={} cc={} subject={}", sender, to, cc, subject);
            return true;
        } catch (Exception e) {
            log.error("[MAIL:FAILED] to={} cc={} subject={} err={}", to, cc, subject, e.getMessage(), e);
            return false;
        }
    }

    /** 憑證齊備且啟用才真的寄；否則退化為 log-only。 */
    private boolean canSend() {
        return enabled
                && javaMailSender != null
                && username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    /** 寄件者：優先 app.mail.from，否則以 SMTP 驗證帳號為準。 */
    private String resolveFrom() {
        if (from != null && !from.isBlank()) {
            return from;
        }
        return username;
    }
}
