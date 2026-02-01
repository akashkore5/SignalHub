package com.khetisetu.event.notifications.provider;

public interface EmailSender {
    void sendEmail(String fromEmail, String fromName, String toEmail, String subject, String htmlBody);

    String getProviderName();
}
