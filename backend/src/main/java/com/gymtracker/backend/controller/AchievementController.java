package com.gymtracker.backend.controller;


import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/achievements")
public class AchievementController {
    
    @GetMapping("/stats")
    public Map<String, Object> getAchievementStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("message", "No achievements available");
        return stats;
    }
}
