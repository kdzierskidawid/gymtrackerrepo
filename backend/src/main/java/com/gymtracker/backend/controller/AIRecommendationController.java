package com.gymtracker.backend.controller;

import com.gymtracker.backend.model.User;
import com.gymtracker.backend.model.TrainingSession;
import com.gymtracker.backend.model.TrainingPlan;
import com.gymtracker.backend.model.PlannedSession;
import com.gymtracker.backend.repository.TrainingSessionRepository;
import com.gymtracker.backend.repository.TrainingPlanRepository;
import com.gymtracker.backend.repository.UserRepository;
import com.gymtracker.backend.repository.PlannedSessionRepository;
import com.gymtracker.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai-recommendations")
public class AIRecommendationController {
    @Autowired private TrainingSessionRepository sessionRepo;
    @Autowired private TrainingPlanRepository planRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private PlannedSessionRepository plannedRepo;
    @Autowired private JwtUtil jwtUtil;

    private User getUserFromToken(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtUtil.extractUsername(token);
        return userRepo.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }

    @PostMapping("/workout-suggestions")
    public Map<String, Object> getWorkoutSuggestions(@RequestHeader("Authorization") String authHeader,
                                                     @RequestBody Map<String, Object> preferences) {
        User user = getUserFromToken(authHeader);
        
        // Get user preferences
        int availableTime = (Integer) preferences.getOrDefault("availableTime", 60); // minutes
        String equipment = (String) preferences.getOrDefault("equipment", "full_gym");
        String fitnessGoal = (String) preferences.getOrDefault("fitnessGoal", "general_fitness");
        
        // Analyze recent training history
        List<TrainingSession> recentSessions = sessionRepo.findByUserAndDateAfterOrderByDateDesc(
            user, LocalDate.now().minusDays(14));
        
        Map<String, Object> result = new HashMap<>();
        
        // Generate personalized recommendations
        List<Map<String, Object>> recommendations = generateRecommendations(
            user, recentSessions, availableTime, equipment, fitnessGoal);
        
        result.put("recommendations", recommendations);
        result.put("basedOnData", getAnalysisSummary(recentSessions));
        result.put("nextSuggestedDate", getNextSuggestedTrainingDate(recentSessions));
        
        return result;
    }

    @GetMapping("/quick-suggestion")
    public Map<String, Object> getQuickSuggestion(@RequestHeader("Authorization") String authHeader) {
        User user = getUserFromToken(authHeader);
        List<TrainingSession> recentSessions = sessionRepo.findByUserAndDateAfterOrderByDateDesc(
            user, LocalDate.now().minusDays(7));
        
        Map<String, Object> result = new HashMap<>();
        
        if (recentSessions.isEmpty()) {
            result.put("suggestion", "Start with a full-body workout to establish your baseline");
            result.put("recommendedPlan", "Beginner Full Body");
            result.put("reason", "No recent training history");
        } else {
            // Analyze what muscle groups were trained recently
            Set<String> recentMuscleGroups = recentSessions.stream()
                .map(s -> extractMuscleGroup(s.getNotes()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            
            String suggestion = generateQuickSuggestion(recentMuscleGroups, recentSessions);
            result.put("suggestion", suggestion);
            result.put("reason", "Based on recent training pattern");
        }
        
        return result;
    }

    @GetMapping("/recovery-status")
    public Map<String, Object> getRecoveryStatus(@RequestHeader("Authorization") String authHeader) {
        User user = getUserFromToken(authHeader);
        List<TrainingSession> recentSessions = sessionRepo.findByUserAndDateAfterOrderByDateDesc(
            user, LocalDate.now().minusDays(7));
        
        Map<String, Object> result = new HashMap<>();
        
        if (recentSessions.isEmpty()) {
            result.put("status", "rested");
            result.put("readiness", 100);
            result.put("recommendation", "You're well-rested and ready for an intense workout!");
            return result;
        }
        
        // Calculate days since last workout
        LocalDate lastWorkout = recentSessions.get(0).getDate();
        long daysSinceLastWorkout = ChronoUnit.DAYS.between(lastWorkout, LocalDate.now());
        
        // Calculate training frequency in last week
        long sessionsThisWeek = recentSessions.stream()
            .filter(s -> ChronoUnit.DAYS.between(s.getDate(), LocalDate.now()) <= 7)
            .count();
        
        // Simple recovery algorithm
        int readiness;
        String status;
        String recommendation;
        
        if (daysSinceLastWorkout >= 3) {
            readiness = 100;
            status = "fully_recovered";
            recommendation = "You're fully recovered! Perfect time for a challenging workout.";
        } else if (daysSinceLastWorkout >= 2) {
            readiness = 85;
            status = "recovered";
            recommendation = "Good recovery status. You can train at high intensity.";
        } else if (daysSinceLastWorkout == 1) {
            readiness = sessionsThisWeek >= 4 ? 60 : 75;
            status = sessionsThisWeek >= 4 ? "moderate_fatigue" : "slightly_fatigued";
            recommendation = sessionsThisWeek >= 4 ? 
                "Consider a light session or active recovery." :
                "You can train, but consider reducing intensity slightly.";
        } else {
            readiness = 45;
            status = "high_fatigue";
            recommendation = "High training frequency detected. Consider rest or very light activity.";
        }
        
        result.put("status", status);
        result.put("readiness", readiness);
        result.put("recommendation", recommendation);
        result.put("daysSinceLastWorkout", daysSinceLastWorkout);
        result.put("sessionsThisWeek", sessionsThisWeek);
        
        return result;
    }

    @PostMapping("/schedule-recommendation")
    public Map<String, Object> scheduleRecommendation(@RequestHeader("Authorization") String authHeader,
                                                      @RequestBody Map<String, Object> body) {
        User user = getUserFromToken(authHeader);
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Extract recommendation data
            String title = (String) body.get("title");
            String description = (String) body.get("description");
            String type = (String) body.get("type");
            String targetMuscle = (String) body.get("targetMuscle");
            String intensity = (String) body.get("intensity");
            String dateStr = (String) body.get("date");
            Integer estimatedTime = (Integer) body.get("estimatedTime");
            
            if (dateStr == null || title == null) {
                result.put("error", "Date and title are required");
                return result;
            }
            
            LocalDate plannedDate = LocalDate.parse(dateStr);
            
            // Create planned session from recommendation
            PlannedSession plannedSession = new PlannedSession();
            plannedSession.setUser(user);
            plannedSession.setDate(plannedDate);
            
            // Format notes with recommendation details
            StringBuilder notes = new StringBuilder();
            notes.append("AI Recommendation: ").append(title).append("\n");
            notes.append("Description: ").append(description).append("\n");
            
            if (type != null) {
                notes.append("Type: ").append(type).append("\n");
            }
            if (targetMuscle != null) {
                notes.append("Target: [").append(targetMuscle).append("]\n");
            }
            if (intensity != null) {
                notes.append("Intensity: ").append(intensity).append("\n");
            }
            if (estimatedTime != null) {
                notes.append("Estimated Time: ").append(estimatedTime).append(" minutes\n");
            }
            
            plannedSession.setNotes(notes.toString().trim());
            
            // Save the planned session
            PlannedSession savedSession = plannedRepo.save(plannedSession);
            
            result.put("success", true);
            result.put("message", "Recommendation scheduled successfully");
            result.put("plannedSession", savedSession);
            
        } catch (Exception e) {
            result.put("error", "Failed to schedule recommendation: " + e.getMessage());
        }
        
        return result;
    }

    private List<Map<String, Object>> generateRecommendations(User user, List<TrainingSession> recentSessions,
                                                              int availableTime, String equipment, String fitnessGoal) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        // Analyze training patterns
        Map<String, Integer> muscleGroupFrequency = analyzeMuscleGroupFrequency(recentSessions);
        long daysSinceLastWorkout = recentSessions.isEmpty() ? 7 : 
            ChronoUnit.DAYS.between(recentSessions.get(0).getDate(), LocalDate.now());
        
        // Recommendation 1: Based on muscle group balance
        String undertrainedMuscle = findUndertrainedMuscleGroup(muscleGroupFrequency);
        if (undertrainedMuscle != null) {
            Map<String, Object> rec1 = new HashMap<>();
            rec1.put("title", "Balance Your Training");
            rec1.put("description", String.format("Focus on %s training to improve muscle balance", undertrainedMuscle));
            rec1.put("type", "muscle_balance");
            rec1.put("targetMuscle", undertrainedMuscle);
            rec1.put("priority", "high");
            rec1.put("estimatedTime", Math.min(availableTime, 45));
            recommendations.add(rec1);
        }
        
        // Recommendation 2: Based on recovery status
        if (daysSinceLastWorkout >= 3) {
            Map<String, Object> rec2 = new HashMap<>();
            rec2.put("title", "High-Intensity Session");
            rec2.put("description", "You're well-rested! Perfect time for a challenging compound movement session");
            rec2.put("type", "intensity");
            rec2.put("intensity", "high");
            rec2.put("priority", "medium");
            rec2.put("estimatedTime", availableTime);
            recommendations.add(rec2);
        } else if (daysSinceLastWorkout <= 1) {
            Map<String, Object> rec2 = new HashMap<>();
            rec2.put("title", "Active Recovery");
            rec2.put("description", "Light cardio and stretching to promote recovery");
            rec2.put("type", "recovery");
            rec2.put("intensity", "low");
            rec2.put("priority", "medium");
            rec2.put("estimatedTime", Math.min(availableTime, 30));
            recommendations.add(rec2);
        }
        
        // Recommendation 3: Based on fitness goal
        Map<String, Object> rec3 = createGoalBasedRecommendation(fitnessGoal, availableTime, equipment);
        if (rec3 != null) {
            recommendations.add(rec3);
        }
        
        // Recommendation 4: Quick option for time constraints
        if (availableTime <= 30) {
            Map<String, Object> rec4 = new HashMap<>();
            rec4.put("title", "Quick HIIT Session");
            rec4.put("description", "High-intensity interval training for maximum efficiency");
            rec4.put("type", "time_efficient");
            rec4.put("intensity", "high");
            rec4.put("priority", "high");
            rec4.put("estimatedTime", availableTime);
            rec4.put("exercises", Arrays.asList("Burpees", "Mountain Climbers", "Jump Squats", "Push-ups"));
            recommendations.add(rec4);
        }
        
        return recommendations.stream()
            .sorted((a, b) -> {
                String priorityA = (String) a.get("priority");
                String priorityB = (String) b.get("priority");
                return getPriorityValue(priorityB) - getPriorityValue(priorityA);
            })
            .limit(3)
            .collect(Collectors.toList());
    }

    private Map<String, Integer> analyzeMuscleGroupFrequency(List<TrainingSession> sessions) {
        Map<String, Integer> frequency = new HashMap<>();
        
        sessions.forEach(session -> {
            String muscleGroup = extractMuscleGroup(session.getNotes());
            if (muscleGroup != null) {
                frequency.merge(muscleGroup, 1, Integer::sum);
            }
        });
        
        return frequency;
    }

    private String findUndertrainedMuscleGroup(Map<String, Integer> frequency) {
        if (frequency.isEmpty()) return "chest"; // Default suggestion
        
        String[] majorMuscleGroups = {"chest", "back", "legs", "shoulders", "arms"};
        String leastTrained = null;
        int minCount = Integer.MAX_VALUE;
        
        for (String muscle : majorMuscleGroups) {
            int count = frequency.getOrDefault(muscle, 0);
            if (count < minCount) {
                minCount = count;
                leastTrained = muscle;
            }
        }
        
        return leastTrained;
    }

    private Map<String, Object> createGoalBasedRecommendation(String goal, int availableTime, String equipment) {
        Map<String, Object> rec = new HashMap<>();
        
        switch (goal.toLowerCase()) {
            case "strength":
                rec.put("title", "Strength Building Session");
                rec.put("description", "Focus on compound movements with heavy weights and low reps");
                rec.put("type", "strength");
                rec.put("intensity", "high");
                rec.put("priority", "medium");
                rec.put("estimatedTime", Math.max(45, availableTime));
                rec.put("exercises", Arrays.asList("Deadlifts", "Squats", "Bench Press", "Overhead Press"));
                break;
            
            case "muscle_building":
                rec.put("title", "Hypertrophy Training");
                rec.put("description", "Moderate weights, higher volume for muscle growth");
                rec.put("type", "hypertrophy");
                rec.put("intensity", "medium-high");
                rec.put("priority", "medium");
                rec.put("estimatedTime", availableTime);
                rec.put("exercises", Arrays.asList("Incline Press", "Rows", "Leg Press", "Lateral Raises"));
                break;
            
            case "endurance":
                rec.put("title", "Endurance Circuit");
                rec.put("description", "High rep, low weight circuit training");
                rec.put("type", "endurance");
                rec.put("intensity", "medium");
                rec.put("priority", "medium");
                rec.put("estimatedTime", availableTime);
                rec.put("exercises", Arrays.asList("Bodyweight Squats", "Push-ups", "Lunges", "Planks"));
                break;
            
            default:
                rec.put("title", "General Fitness");
                rec.put("description", "Balanced workout combining strength and cardio");
                rec.put("type", "general");
                rec.put("intensity", "medium");
                rec.put("priority", "low");
                rec.put("estimatedTime", availableTime);
                break;
        }
        
        return rec;
    }

    private String generateQuickSuggestion(Set<String> recentMuscleGroups, List<TrainingSession> recentSessions) {
        long daysSinceLastWorkout = ChronoUnit.DAYS.between(
            recentSessions.get(0).getDate(), LocalDate.now());
        
        if (daysSinceLastWorkout >= 3) {
            return "You're well-rested! Time for a challenging full-body session.";
        } else if (daysSinceLastWorkout >= 2) {
            // Suggest complementary muscle group
            if (recentMuscleGroups.contains("chest") || recentMuscleGroups.contains("shoulders")) {
                return "Focus on back and biceps today for balance.";
            } else if (recentMuscleGroups.contains("back")) {
                return "Great time for chest and triceps work.";
            } else if (recentMuscleGroups.contains("legs")) {
                return "Perfect day for upper body training.";
            } else {
                return "Consider a leg-focused workout today.";
            }
        } else {
            return "Recent training detected. Consider light cardio or stretching.";
        }
    }

    private Map<String, Object> getAnalysisSummary(List<TrainingSession> recentSessions) {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("totalSessions", recentSessions.size());
        summary.put("averageFrequency", recentSessions.size() / 2.0); // per week
        
        if (!recentSessions.isEmpty()) {
            long daysSinceLastWorkout = ChronoUnit.DAYS.between(
                recentSessions.get(0).getDate(), LocalDate.now());
            summary.put("daysSinceLastWorkout", daysSinceLastWorkout);
            
            Map<String, Integer> muscleGroups = analyzeMuscleGroupFrequency(recentSessions);
            summary.put("trainedMuscleGroups", muscleGroups.keySet());
        }
        
        return summary;
    }

    private String getNextSuggestedTrainingDate(List<TrainingSession> recentSessions) {
        if (recentSessions.isEmpty()) {
            return LocalDate.now().toString();
        }
        
        LocalDate lastWorkout = recentSessions.get(0).getDate();
        long daysSince = ChronoUnit.DAYS.between(lastWorkout, LocalDate.now());
        
        if (daysSince >= 2) {
            return LocalDate.now().toString();
        } else {
            return lastWorkout.plusDays(2).toString();
        }
    }

    private String extractMuscleGroup(String notes) {
        if (notes == null) return null;
        
        // Extract muscle group from [muscleGroup] format
        if (notes.contains("[") && notes.contains("]")) {
            int start = notes.indexOf("[") + 1;
            int end = notes.indexOf("]");
            if (end > start) {
                return notes.substring(start, end).toLowerCase();
            }
        }
        
        // Fallback to keyword detection
        String[] muscleGroups = {"chest", "back", "legs", "shoulders", "arms", "biceps", "triceps", "abs"};
        String lowerNotes = notes.toLowerCase();
        for (String muscle : muscleGroups) {
            if (lowerNotes.contains(muscle)) {
                return muscle;
            }
        }
        
        return null;
    }

    private int getPriorityValue(String priority) {
        switch (priority.toLowerCase()) {
            case "high": return 3;
            case "medium": return 2;
            case "low": return 1;
            default: return 0;
        }
    }
}
