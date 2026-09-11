package com.loganalyzer.alert.service;

import com.loganalyzer.alert.model.AnomalyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender emailSender;

    @Value("${alert.recipient}")
    private String recipientEmail;

    public void sendAnomalyAlert(AnomalyEvent event) {
        log.info("Preparing to send email alert for anomaly in service: {}", event.getServiceId());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject("CRITICAL ALERT: Anomaly Detected in " + event.getServiceId());
            
            String timeStr = event.getTimestamp() == null
                    ? "unknown"
                    : event.getTimestamp()
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String text = String.format("""
                    ATTENTION DEVOPS TEAM,
                    
                    An anomaly has been detected in the production environment.
                    
                    Details:
                    - Service ID: %s
                    - Severity: %s
                    - Detector: %s
                    - Project ID: %s
                    - Detection Time: %s
                    
                    Please investigate immediately.
                    
                    - Intelligent Log Analyzer System
                    """, 
                    event.getServiceId(), 
                    event.getSeverity(),
                    event.getDetector(),
                    event.getProjectId(),
                    timeStr);

            message.setText(text);
            
            // In a real environment with correct SMTP, this sends the email.
            emailSender.send(message);
            
            log.warn("=================================================");
            log.warn("EMAIL ALERT SENT TO: {}", recipientEmail);
            log.warn("SUBJECT: {}", message.getSubject());
            log.warn("BODY:\n{}", text);
            log.warn("=================================================");
            
        } catch (Exception e) {
            log.error("Failed to send email alert", e);
        }
    }
}
