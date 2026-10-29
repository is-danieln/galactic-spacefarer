package com.galactic.spacefarer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class WelcomeEmail {
    private static final Logger log = LoggerFactory.getLogger(WelcomeEmail.class);
    private final JavaMailSender mailSender;
    private final String from;

    public WelcomeEmail(JavaMailSender mailSender, @Value("${galactic.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String recipient, String name) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Welcome aboard, spacefarer!");
        message.setText("Hello " + name + ",\n\nCongratulations on starting your galactic adventure! "
                + "Your spacefarer profile is ready. May your journeys be full of stardust.\n\nMission Control");
        try {
            mailSender.send(message);
        } catch (MailException failure) {
            // a rekord már mentve van, a levél hibája nem vonja vissza
            // automatikus újrapróbálás nincs
            log.warn("Az üdvözlőlevél küldése sikertelen ({}). Ellenőrizd az SMTP-szolgáltatást.", failure.getClass().getSimpleName());
        }
    }
}
