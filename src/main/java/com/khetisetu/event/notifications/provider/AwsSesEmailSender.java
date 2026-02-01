package com.khetisetu.event.notifications.provider;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Component
@Slf4j
public class AwsSesEmailSender implements EmailSender {

    private final SesClient sesClient;

    private static final String SES_CB = "sesService";

    public AwsSesEmailSender(
            @Value("${aws.accessKeyId:}") String accessKey,
            @Value("${aws.secretKey:}") String secretKey,
            @Value("${aws.region:ap-south-1}") String region) {

        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            this.sesClient = SesClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
            log.info("AWS SES Client initialized for region: {}", region);
        } else {
            // Check if default chain works or just warn
            log.warn("AWS Credentials not provided in properties. Using DefaultChain or No-Op.");
            // We can fallback to default chain if that's preferred, but for this specific
            // setup:
            this.sesClient = SesClient.builder().region(Region.of(region)).build();
        }
    }

    @Override
    public String getProviderName() {
        return "AWS_SES";
    }

    @Override
    @CircuitBreaker(name = SES_CB, fallbackMethod = "fallbackSendEmail")
    public void sendEmail(String fromEmail, String fromName, String toEmail, String subject, String htmlBody) {
        try {
            // SES 'Source' format: "Name <email>"
            String source = fromName != null && !fromName.isEmpty()
                    ? String.format("%s <%s>", fromName, fromEmail)
                    : fromEmail;

            SendEmailRequest request = SendEmailRequest.builder()
                    .source(source)
                    .destination(Destination.builder().toAddresses(toEmail).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();

            SendEmailResponse response = sesClient.sendEmail(request);
            log.info("Email sent via AWS SES to {}. MessageId: {}", toEmail, response.messageId());

        } catch (SesException e) {
            log.error("AWS SES Error: {}", e.awsErrorDetails().errorMessage());
            throw new RuntimeException("AWS SES Error", e);
        } catch (Exception e) {
            log.error("Failed to send email via AWS SES", e);
            throw new RuntimeException("Failed to send email via AWS SES", e);
        }
    }

    public void fallbackSendEmail(String fromEmail, String fromName, String toEmail, String subject, String htmlBody,
            Throwable t) {
        log.error("Fallback: circuit breaker open or error for SES. Reason: {}", t.getMessage());
        throw new RuntimeException("AWS SES unavailable: " + t.getMessage(), t);
    }
}
