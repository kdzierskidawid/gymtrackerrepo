package com.gymtracker.backend.controller;

import com.gymtracker.backend.model.User;
import com.gymtracker.backend.model.TrainingSession;
import com.gymtracker.backend.repository.TrainingSessionRepository;
import com.gymtracker.backend.repository.UserRepository;
import com.gymtracker.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    @Autowired private TrainingSessionRepository sessionRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private JwtUtil jwtUtil;

    private User getUserFromToken(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtUtil.extractUsername(token);
        return userRepo.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/volume")
    public Map<String, Object> getVolumeAnalytics(@RequestHeader("Authorization") String authHeader,
                                                  @RequestParam(defaultValue = "30") int days) {
        User user = getUserFromToken(authHeader);
        LocalDate startDate = LocalDate.now().minusDays(days);
        
        List<TrainingSession> sessions = sessionRepo.findByUserAndDateAfterOrderByDateDesc(user, startDate);
        
        Map<String, Object> result = new HashMap<>();
        
        // Weekly volume tracking
        Map<String, Double> weeklyVolume = new LinkedHashMap<>();
        Map<String, Integer> weeklySessions = new LinkedHashMap<>();
        
        // Group sessions by week
        sessions.forEach(session -> {
            String weekKey = getWeekKey(session.getDate());
            double volume = calculateSessionVolume(session);
            
            weeklyVolume.merge(weekKey, volume, Double::sum);
            weeklySessions.merge(weekKey, 1, Integer::sum);
        });
        
        result.put("weeklyVolume", weeklyVolume);
        result.put("weeklySessions", weeklySessions);
        result.put("totalVolume", sessions.stream().mapToDouble(this::calculateSessionVolume).sum());
        result.put("totalSessions", sessions.size());
        
        return result;
    }

    @GetMapping("/muscle-balance")
    public Map<String, Object> getMuscleGroupBalance(@RequestHeader("Authorization") String authHeader,
                                                     @RequestParam(defaultValue = "30") int days) {
        User user = getUserFromToken(authHeader);
        LocalDate startDate = LocalDate.now().minusDays(days);
        
        List<TrainingSession> sessions = sessionRepo.findByUserAndDateAfterOrderByDateDesc(user, startDate);
        
        Map<String, Integer> muscleGroupCount = new HashMap<>();
        Map<String, Double> muscleGroupVolume = new HashMap<>();
        
        sessions.forEach(session -> {
            String muscleGroup = extractMuscleGroup(session.getNotes());
            if (muscleGroup != null) {
                muscleGroupCount.merge(muscleGroup, 1, Integer::sum);
                muscleGroupVolume.merge(muscleGroup, calculateSessionVolume(session), Double::sum);
            }
        });
        
        Map<String, Object> result = new HashMap<>();
        result.put("muscleGroupCount", muscleGroupCount);
        result.put("muscleGroupVolume", muscleGroupVolume);
        result.put("recommendations", generateBalanceRecommendations(muscleGroupCount));
        
        return result;
    }

    @GetMapping("/performance-trends")
    public Map<String, Object> getPerformanceTrends(@RequestHeader("Authorization") String authHeader) {
        User user = getUserFromToken(authHeader);
        List<TrainingSession> sessions = sessionRepo.findByUserOrderByDateDesc(user);
          if (sessions.size() < 5) {
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Need at least 5 sessions for trend analysis");
            return result;
        }
        
        // Analyze last 3 months of data
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<TrainingSession> recentSessions = sessions.stream()
            .filter(s -> s.getDate().isAfter(threeMonthsAgo))
            .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        
        // Performance trend (improvement/plateau/decline)
        String trend = calculatePerformanceTrend(recentSessions);
        result.put("trend", trend);
        
        // Plateau detection
        boolean plateauDetected = detectPlateau(recentSessions);
        result.put("plateauDetected", plateauDetected);
        
        // Best performance periods
        Map<String, Object> bestPeriod = findBestPerformancePeriod(recentSessions);
        result.put("bestPeriod", bestPeriod);
        
        return result;
    }

    @GetMapping("/recovery-recommendations")
    public Map<String, Object> getRecoveryRecommendations(@RequestHeader("Authorization") String authHeader) {
        User user = getUserFromToken(authHeader);
        List<TrainingSession> recentSessions = sessionRepo.findByUserAndDateAfterOrderByDateDesc(
            user, LocalDate.now().minusDays(14));
        
        Map<String, Object> result = new HashMap<>();
        
        if (recentSessions.isEmpty()) {
            result.put("message", "No recent training data");
            return result;
        }
        
        // Calculate training frequency
        double averageGapDays = calculateAverageRestDays(recentSessions);
        
        // Recovery recommendations based on frequency and volume
        List<String> recommendations = new ArrayList<>();
        
        if (averageGapDays < 1.0) {
            recommendations.add("Consider adding more rest days between sessions");
            recommendations.add("Your training frequency is very high - ensure adequate sleep and nutrition");
        } else if (averageGapDays > 3.0) {
            recommendations.add("You could increase training frequency for better progress");
            recommendations.add("Try to maintain consistency with 3-4 sessions per week");
        } else {
            recommendations.add("Good training frequency! Maintain current schedule");
        }
        
        // Volume-based recommendations
        double avgVolume = recentSessions.stream().mapToDouble(this::calculateSessionVolume).average().orElse(0);
        if (avgVolume > 50000) { // High volume threshold
            recommendations.add("High training volume detected - ensure adequate recovery");
            recommendations.add("Consider deload week every 4-6 weeks");
        }
        
        result.put("averageRestDays", averageGapDays);
        result.put("recommendations", recommendations);
        result.put("trainingFrequency", recentSessions.size() / 14.0 * 7); // sessions per week
        
        return result;
    }

    // Helper methods
    private double calculateSessionVolume(TrainingSession session) {
        if (session.getWeight() == null) return 0.0;
        // Estimate volume as weight * estimated sets * estimated reps
        // This is a simplified calculation - in real app you'd want more detailed exercise tracking
        return session.getWeight() * 15; // Assuming average 3 sets of 5 reps
    }

    private String getWeekKey(LocalDate date) {
        // Get week starting Monday
        LocalDate monday = date.minusDays(date.getDayOfWeek().getValue() - 1);
        return monday.toString();
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
        String[] muscleGroups = {"chest", "back", "legs", "shoulders", "arms", "biceps", "triceps", "abs", "glutes"};
        String lowerNotes = notes.toLowerCase();
        for (String muscle : muscleGroups) {
            if (lowerNotes.contains(muscle)) {
                return muscle;
            }
        }
        
        return "other";
    }

    private List<String> generateBalanceRecommendations(Map<String, Integer> muscleGroupCount) {
        List<String> recommendations = new ArrayList<>();
        
        if (muscleGroupCount.isEmpty()) {
            recommendations.add("Start tracking muscle groups in your notes for better analysis");
            return recommendations;
        }
        
        int maxCount = Collections.max(muscleGroupCount.values());
        int minCount = Collections.min(muscleGroupCount.values());
        
        if (maxCount > minCount * 2) {
            String mostTrained = muscleGroupCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
            
            String leastTrained = muscleGroupCount.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
            
            recommendations.add(String.format("Consider more %s training to balance with %s", leastTrained, mostTrained));
        } else {
            recommendations.add("Good muscle group balance!");
        }
        
        return recommendations;
    }

    private String calculatePerformanceTrend(List<TrainingSession> sessions) {
        if (sessions.size() < 5) return "insufficient_data";
        
        // Simple trend analysis based on average weights over time
        List<TrainingSession> sortedSessions = sessions.stream()
            .sorted(Comparator.comparing(TrainingSession::getDate))
            .collect(Collectors.toList());
        
        double firstHalfAvg = sortedSessions.subList(0, sortedSessions.size() / 2).stream()
            .filter(s -> s.getWeight() != null)
            .mapToDouble(TrainingSession::getWeight)
            .average().orElse(0);
        
        double secondHalfAvg = sortedSessions.subList(sortedSessions.size() / 2, sortedSessions.size()).stream()
            .filter(s -> s.getWeight() != null)
            .mapToDouble(TrainingSession::getWeight)
            .average().orElse(0);
        
        double improvement = (secondHalfAvg - firstHalfAvg) / firstHalfAvg * 100;
        
        if (improvement > 5) return "improving";
        else if (improvement < -5) return "declining";
        else return "stable";
    }

    private boolean detectPlateau(List<TrainingSession> sessions) {
        if (sessions.size() < 6) return false;
        
        // Check if last 6 sessions show no improvement
        List<TrainingSession> lastSix = sessions.stream()
            .sorted(Comparator.comparing(TrainingSession::getDate).reversed())
            .limit(6)
            .filter(s -> s.getWeight() != null)
            .collect(Collectors.toList());
        
        if (lastSix.size() < 6) return false;
        
        Double maxWeight = lastSix.stream()
            .mapToDouble(TrainingSession::getWeight)
            .max().orElse(0);
        
        Double avgWeight = lastSix.stream()
            .mapToDouble(TrainingSession::getWeight)
            .average().orElse(0);
        
        // Plateau if max weight is not significantly higher than average
        return (maxWeight - avgWeight) / avgWeight < 0.05; // Less than 5% variation
    }

    private Map<String, Object> findBestPerformancePeriod(List<TrainingSession> sessions) {
        // Find 2-week period with highest average performance
        Map<String, Object> bestPeriod = new HashMap<>();
        
        if (sessions.size() < 3) {
            bestPeriod.put("message", "Insufficient data");
            return bestPeriod;
        }
        
        List<TrainingSession> sortedSessions = sessions.stream()
            .sorted(Comparator.comparing(TrainingSession::getDate))
            .filter(s -> s.getWeight() != null)
            .collect(Collectors.toList());
        
        double bestAvg = 0;
        LocalDate bestStartDate = null;
        
        for (int i = 0; i < sortedSessions.size() - 2; i++) {
            LocalDate startDate = sortedSessions.get(i).getDate();
            LocalDate endDate = startDate.plusDays(14);
            
            List<TrainingSession> periodSessions = sortedSessions.stream()
                .filter(s -> !s.getDate().isBefore(startDate) && !s.getDate().isAfter(endDate))
                .collect(Collectors.toList());
            
            if (periodSessions.size() >= 2) {
                double avgWeight = periodSessions.stream()
                    .mapToDouble(TrainingSession::getWeight)
                    .average().orElse(0);
                
                if (avgWeight > bestAvg) {
                    bestAvg = avgWeight;
                    bestStartDate = startDate;
                }
            }
        }
        
        bestPeriod.put("startDate", bestStartDate);
        bestPeriod.put("averageWeight", bestAvg);
        bestPeriod.put("endDate", bestStartDate != null ? bestStartDate.plusDays(14) : null);
        
        return bestPeriod;
    }

    private double calculateAverageRestDays(List<TrainingSession> sessions) {
        if (sessions.size() < 2) return 0;
        
        List<TrainingSession> sortedSessions = sessions.stream()
            .sorted(Comparator.comparing(TrainingSession::getDate))
            .collect(Collectors.toList());
        
        double totalGapDays = 0;
        for (int i = 1; i < sortedSessions.size(); i++) {
            long daysBetween = ChronoUnit.DAYS.between(
                sortedSessions.get(i-1).getDate(),
                sortedSessions.get(i).getDate()
            );
            totalGapDays += daysBetween;
        }
        
        return totalGapDays / (sortedSessions.size() - 1);
    }
}
