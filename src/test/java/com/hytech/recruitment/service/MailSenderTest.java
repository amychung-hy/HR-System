package com.hytech.recruitment.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 寄信器單元測試：log-only 退化、真實寄送成功／退信。 */
@DisplayName("寄信器")
@ExtendWith(MockitoExtension.class)
class MailSenderTest {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private MailSender mailSender;

    private void configureCredentials(boolean enabled, String username, String password) {
        ReflectionTestUtils.setField(mailSender, "enabled", enabled);
        ReflectionTestUtils.setField(mailSender, "username", username);
        ReflectionTestUtils.setField(mailSender, "password", password);
        ReflectionTestUtils.setField(mailSender, "from", "");
    }

    @Test
    @DisplayName("未設定憑證時退化為 log-only 且回 true")
    void noCredentials_degradesToLogOnly_returnsTrue() {
        configureCredentials(true, "", "");
        boolean ok = mailSender.send("to@x.com", null, "主旨", "<p>hi</p>");
        assertTrue(ok);
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("停用時退化為 log-only")
    void disabled_degradesToLogOnly() {
        configureCredentials(false, "u@x.com", "pw");
        assertTrue(mailSender.send("to@x.com", "cc@x.com", "主旨", "body"));
    }

    @Test
    @DisplayName("憑證齊備時真實寄送成功回 true")
    void withCredentials_sendsSuccessfully_returnsTrue() {
        configureCredentials(true, "u@x.com", "pw");
        when(javaMailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        boolean ok = mailSender.send("to@x.com", "cc1@x.com, cc2@x.com", "主旨", "<p>body</p>");
        assertTrue(ok);
        verify(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("寄送拋例外時回 false 代表退信")
    void sendThrows_returnsFalse_asBounce() {
        configureCredentials(true, "u@x.com", "pw");
        when(javaMailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        doThrow(new RuntimeException("SMTP down")).when(javaMailSender).send(any(MimeMessage.class));
        assertFalse(mailSender.send("to@x.com", null, "主旨", "body"));
    }

    private static MimeMessage any(Class<MimeMessage> c) {
        return org.mockito.ArgumentMatchers.any(c);
    }
}
