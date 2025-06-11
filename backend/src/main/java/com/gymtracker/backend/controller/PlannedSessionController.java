package com.gymtracker.backend.controller;

import com.gymtracker.backend.model.PlannedSession;
import com.gymtracker.backend.model.TrainingPlan;
import com.gymtracker.backend.model.User;
import com.gymtracker.backend.repository.PlannedSessionRepository;
import com.gymtracker.backend.repository.TrainingPlanRepository;
import com.gymtracker.backend.repository.UserRepository;
import com.gymtracker.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/training/planned")
public class PlannedSessionController {
    @Autowired private PlannedSessionRepository plannedRepo;
    @Autowired private TrainingPlanRepository planRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private JwtUtil jwtUtil;

    private User getUserFromToken(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtUtil.extractUsername(token);
        return userRepo.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping
    public List<PlannedSession> getPlanned(@RequestHeader("Authorization") String authHeader) {
        User user = getUserFromToken(authHeader);
        return plannedRepo.findByUserOrderByDateAsc(user);
    }

    @PostMapping
    public PlannedSession addPlanned(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> body) {
        User user = getUserFromToken(authHeader);
        PlannedSession session = new PlannedSession();
        session.setUser(user);
        if (body.get("plan") != null) {
            Map<String, Object> planMap = (Map<String, Object>) body.get("plan");
            if (planMap.get("id") != null) {
                Long planId = Long.valueOf(planMap.get("id").toString());
                planRepo.findById(planId).ifPresent(session::setPlan);
            }
        }
        session.setDate(LocalDate.parse((String) body.get("date")));
        session.setNotes((String) body.get("notes"));
        return plannedRepo.save(session);
    }    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlanned(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        User user = getUserFromToken(authHeader);
        Optional<PlannedSession> session = plannedRepo.findById(id);
        if (session.isPresent() && session.get().getUser().getId().equals(user.getId())) {
            plannedRepo.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(403).body("Not allowed");
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<PlannedSession> markAsCompleted(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        User user = getUserFromToken(authHeader);
        Optional<PlannedSession> sessionOpt = plannedRepo.findById(id);
        if (sessionOpt.isPresent() && sessionOpt.get().getUser().getId().equals(user.getId())) {
            PlannedSession session = sessionOpt.get();
            session.setCompleted(true);
            PlannedSession savedSession = plannedRepo.save(session);
            return ResponseEntity.ok(savedSession);
        }
        return ResponseEntity.status(403).build();
    }
}
