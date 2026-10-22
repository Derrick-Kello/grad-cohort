package edu.university.grps.notification.service;

import edu.university.grps.common.GrpsProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Configuration
public class EmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfig.class);

    @Bean
    public EmailSender emailSender(GrpsProperties props, JavaMailSender mailSender) {
        if (!props.mail().enabled()) {
            return (to, subject, html, text) -> log.info("[email stub] to={} subject=\"{}\"\n{}", to, subject, text);
        }
        return (to, subject, html, text) -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(props.mail().from());
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(text, html);
                mailSender.send(message);
            } catch (jakarta.mail.MessagingException e) {
                throw new IllegalStateException("Could not build email", e);
            }
        };
    }
}
