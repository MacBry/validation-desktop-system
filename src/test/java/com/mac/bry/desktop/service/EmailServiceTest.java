package com.mac.bry.desktop.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private EmailService emailService;

    // ===== 1.6 EmailService — Wysyłka Powiadomień =====

    @Nested
    @DisplayName("UT-26..30: Wysyłka E-mail")
    class EmailSendingTests {

        @Test
        @DisplayName("UT-26: sendPasswordResetEmail() — poprawny temat i treść")
        void shouldSendPasswordResetEmail() {
            emailService.sendPasswordResetEmail("user@test.com", "TempPass1");

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());

            SimpleMailMessage sent = captor.getValue();
            assertThat(sent.getTo()).containsExactly("user@test.com");
            assertThat(sent.getSubject()).contains("Reset hasła");
            assertThat(sent.getText()).contains("TempPass1");
        }

        @Test
        @DisplayName("UT-27: sendAccountActivatedEmail() — poprawny temat")
        void shouldSendActivationEmail() {
            emailService.sendAccountActivatedEmail("user@test.com", "Jan Kowalski");

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());

            SimpleMailMessage sent = captor.getValue();
            assertThat(sent.getSubject()).contains("aktywowane");
            assertThat(sent.getText()).contains("Jan Kowalski");
        }

        @Test
        @DisplayName("UT-28: sendAccountDeactivatedEmail() — poprawny temat")
        void shouldSendDeactivationEmail() {
            emailService.sendAccountDeactivatedEmail("user@test.com", "Jan Kowalski");

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());

            SimpleMailMessage sent = captor.getValue();
            assertThat(sent.getSubject()).contains("zablokowane");
        }

        @Test
        @DisplayName("UT-29: sendNewUserAdminNotification() — pusta lista adminów, brak wysyłki")
        void shouldNotSendNotificationWhenNoAdmins() {
            emailService.sendNewUserAdminNotification(List.of(), "new@test.com", "Nowy User");

            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("UT-30: sendNewUserAdminNotification() — 2 adminów, wysyłka do obu")
        void shouldSendNotificationToMultipleAdmins() {
            List<String> admins = List.of("admin1@test.com", "admin2@test.com");

            emailService.sendNewUserAdminNotification(admins, "new@test.com", "Nowy User");

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());

            SimpleMailMessage sent = captor.getValue();
            assertThat(sent.getTo()).containsExactlyInAnyOrder("admin1@test.com", "admin2@test.com");
            assertThat(sent.getSubject()).contains("Nowy User");
            assertThat(sent.getText()).contains("new@test.com");
        }
    }
}
