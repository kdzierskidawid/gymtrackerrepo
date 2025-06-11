package com.gymtracker.backend.controller;

import com.gymtracker.backend.model.*;
import com.gymtracker.backend.repository.*;
import com.gymtracker.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/training")
public class TrainingController {
    @Autowired private TrainingPlanRepository planRepo;
    @Autowired private TrainingSessionRepository sessionRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private JwtUtil jwtUtil;

    // DEBUG: Log token and username for all authenticated requests
    private User getUserFromToken(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        System.out.println("[DEBUG] Received token: " + token);
        String username = jwtUtil.extractUsername(token);
        System.out.println("[DEBUG] Extracted username: " + username);
        return userRepo.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Training Plans
    @GetMapping("/plans")
    public List<TrainingPlan> getPlans(@RequestHeader("Authorization") String authHeader) {
        User user = getUserFromToken(authHeader);
        return planRepo.findByUser(user);
    }

    @PostMapping("/plans")
    public TrainingPlan addPlan(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> body) {
        User user = getUserFromToken(authHeader);
        TrainingPlan plan = new TrainingPlan();
        plan.setUser(user);
        plan.setName((String) body.get("name"));
        plan.setDescription((String) body.get("description"));
        plan.setExercises((List<String>) body.getOrDefault("exercises", new ArrayList<>()));
        return planRepo.save(plan);
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<?> deletePlan(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        User user = getUserFromToken(authHeader);
        Optional<TrainingPlan> plan = planRepo.findById(id);
        if (plan.isPresent() && plan.get().getUser().getId().equals(user.getId())) {
            planRepo.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(403).body("Not allowed");
    }

    // Training Sessions
    @GetMapping("/sessions")
    public List<TrainingSession> getSessions(@RequestHeader("Authorization") String authHeader) {
        User user = getUserFromToken(authHeader);
        return sessionRepo.findByUserOrderByDateAsc(user);
    }

    @PostMapping("/sessions")
    public TrainingSession addSession(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> body) {
        User user = getUserFromToken(authHeader);
        TrainingSession session = new TrainingSession();
        session.setUser(user);
        if (body.get("planId") != null) {
            Long planId = Long.valueOf(body.get("planId").toString());
            planRepo.findById(planId).ifPresent(session::setPlan);
        }
        session.setDate(LocalDate.parse((String) body.get("date")));
        session.setWeight(body.get("weight") != null ? Integer.valueOf(body.get("weight").toString()) : null);
        session.setNotes((String) body.get("notes"));
        return sessionRepo.save(session);
    }

    // Clear all training session history for the user
    @DeleteMapping("/sessions")
    public ResponseEntity<?> clearSessions(@RequestHeader("Authorization") String authHeader) {
        User user = getUserFromToken(authHeader);
        List<TrainingSession> sessions = sessionRepo.findByUserOrderByDateAsc(user);
        sessionRepo.deleteAll(sessions);
        return ResponseEntity.ok().build();
    }

    // Progress (weight/BMI history)
    @GetMapping("/progress")
    public List<Map<String, Object>> getProgress(@RequestHeader("Authorization") String authHeader) {
        User user = getUserFromToken(authHeader);
        List<TrainingSession> sessions = sessionRepo.findByUserOrderByDateAsc(user);
        List<Map<String, Object>> progress = new ArrayList<>();
        for (TrainingSession s : sessions) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("date", s.getDate());
            entry.put("weight", s.getWeight());
            if (user.getHeight() != null && s.getWeight() != null) {
                double bmi = s.getWeight() / Math.pow(user.getHeight() / 100.0, 2);
                entry.put("bmi", bmi);
            }
            progress.add(entry);
        }
        return progress;
    }

    // Personal Bests
    @GetMapping("/personal-bests")
    public Map<String, Object> getPersonalBests(@RequestHeader("Authorization") String authHeader) {
        User user = getUserFromToken(authHeader);
        List<TrainingSession> sessions = sessionRepo.findByUserOrderByDateAsc(user);
        Integer heaviestLift = null;
        LocalDate heaviestDate = null;
        String heaviestPlan = null;
        for (TrainingSession s : sessions) {
            if (s.getWeight() != null && (heaviestLift == null || s.getWeight() > heaviestLift)) {
                heaviestLift = s.getWeight();
                heaviestDate = s.getDate();
                heaviestPlan = s.getPlan() != null ? s.getPlan().getName() : null;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("heaviestLift", heaviestLift);
        result.put("heaviestDate", heaviestDate);
        result.put("heaviestPlan", heaviestPlan);
        // Add more personal bests here (e.g., fastest run) as needed
        return result;
    }
}
