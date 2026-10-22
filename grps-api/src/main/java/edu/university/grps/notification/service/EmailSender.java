package edu.university.grps.notification.service;

/** Outbound email (PRD section 24). The v1 implementation is Spring Mail over SMTP; local runs log instead. */
public interface EmailSender {

    void send(String to, String subject, String html, String text);
}
