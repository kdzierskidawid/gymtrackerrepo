package com.gymtracker.backend.service;

import com.gymtracker.backend.model.PlannedSession;
import com.gymtracker.backend.model.User;
import com.gymtracker.backend.repository.PlannedSessionRepository;
import com.gymtracker.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class NotificationService {
    @Autowired private PlannedSessionRepository plannedRepo;
    @Autowired private UserRepository userRepo;
    @Autowired(required = false) private JavaMailSender mailSender;

    // Run every day at 8:00 AM
    @Scheduled(cron = "0 0 8 * * *")
    public void sendPlannedSessionReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<PlannedSession> sessions = plannedRepo.findAll();
        for (PlannedSession session : sessions) {
            if (session.getDate() != null && session.getDate().equals(tomorrow)) {
                User user = session.getUser();
                if (user.getEmail() != null && mailSender != null) {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setTo(user.getEmail());
                    message.setSubject("GymTracker: Training Session Reminder");
                    message.setText("Reminder: You have a planned training session tomorrow (" + session.getDate() + ") for plan: " + (session.getPlan() != null ? session.getPlan().getName() : "No plan") + ".");
                    mailSender.send(message);
                }
            }
        }
    }
}
