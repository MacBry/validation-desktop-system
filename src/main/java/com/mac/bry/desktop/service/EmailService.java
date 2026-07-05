package com.mac.bry.desktop.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final String FROM_ADDRESS;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from:maciej.bryja@wp.pl}") String fromAddress) {
        this.mailSender = mailSender;
        this.FROM_ADDRESS = fromAddress;
    }

    public void sendPasswordResetEmail(String to, String tempPassword) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(FROM_ADDRESS);
            message.setTo(to);
            message.setSubject("Validation System - Reset hasła");
            message.setText("Witaj,\n\n" +
                    "Zlecono reset hasła dla Twojego konta.\n" +
                    "Twoje nowe hasło tymczasowe to: " + tempPassword + "\n\n" +
                    "Przy następnym logowaniu system wymusi na Tobie zmianę hasła na nowe.\n\n" +
                    "Pozdrawiamy,\nZespół Validation System");
            
            mailSender.send(message);
            log.info("Wysłano email z resetem hasła do: {}", to);
        } catch (Exception e) {
            log.error("Nie udało się wysłać emaila z resetem hasła do: {}", to, e);
        }
    }

    public void sendAccountActivatedEmail(String to, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(FROM_ADDRESS);
            message.setTo(to);
            message.setSubject("Validation System - Twoje konto zostało aktywowane!");
            message.setText("Witaj " + name + ",\n\n" +
                    "Twoje konto w systemie Validation System zostało właśnie aktywowane przez Administratora.\n" +
                    "Możesz się już bezpiecznie zalogować do aplikacji.\n\n" +
                    "Pozdrawiamy,\nZespół Validation System");
            
            mailSender.send(message);
            log.info("Wysłano email powitalny (aktywacja) do: {}", to);
        } catch (Exception e) {
            log.error("Nie udało się wysłać emaila aktywacyjnego do: {}", to, e);
        }
    }

    public void sendNewUserAdminNotification(List<String> adminEmails, String newUserEmail, String newUserName) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            log.warn("Brak administratorów do powiadomienia o nowym koncie.");
            return;
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(FROM_ADDRESS);
            message.setTo(adminEmails.toArray(new String[0]));
            message.setSubject("Validation System - Nowa rejestracja: " + newUserName);
            message.setText("Witaj Administratorze,\n\n" +
                    "W systemie zarejestrował się nowy użytkownik:\n" +
                    "Imię i nazwisko: " + newUserName + "\n" +
                    "Adres e-mail: " + newUserEmail + "\n\n" +
                    "Konto oczekuje na weryfikację i aktywację. Zaloguj się do Panelu Administratora, aby nadać role i włączyć konto.\n\n" +
                    "System Powiadomień Validation System");
            
            mailSender.send(message);
            log.info("Wysłano powiadomienie do adminów o nowym koncie: {}", newUserEmail);
        } catch (Exception e) {
            log.error("Nie udało się wysłać powiadomienia do adminów.", e);
        }
    }

    public void sendAccountDeactivatedEmail(String to, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(FROM_ADDRESS);
            message.setTo(to);
            message.setSubject("Validation System - Twoje konto zostało zablokowane");
            message.setText("Witaj " + name + ",\n\n" +
                    "Informujemy, że Twoje konto w systemie Validation System zostało zablokowane przez Administratora.\n" +
                    "Jeśli uważasz, że jest to pomyłka, skontaktuj się z działem administracji.\n\n" +
                    "Pozdrawiamy,\nZespół Validation System");
            
            mailSender.send(message);
            log.info("Wysłano email o zablokowaniu konta do: {}", to);
        } catch (Exception e) {
            log.error("Nie udało się wysłać emaila o zablokowaniu do: {}", to, e);
        }
    }

    public void sendMassEmail(List<String> recipients, String subject, String body) {
        if (recipients == null || recipients.isEmpty()) return;
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(FROM_ADDRESS);
            message.setTo(recipients.toArray(new String[0]));
            message.setSubject(subject);
            message.setText(body + "\n\n---\nWiadomość systemowa Validation System");
            
            mailSender.send(message);
            log.info("Wysłano powiadomienie masowe do {} odbiorców.", recipients.size());
        } catch (Exception e) {
            log.error("Błąd podczas wysyłki wiadomości masowej", e);
        }
    }
}
