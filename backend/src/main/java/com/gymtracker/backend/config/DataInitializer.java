package com.gymtracker.backend.config;

import com.gymtracker.backend.model.TrainingPlanTemplate;
import com.gymtracker.backend.repository.TrainingPlanTemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initTemplates(TrainingPlanTemplateRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                TrainingPlanTemplate t1 = new TrainingPlanTemplate();
                t1.setName("Full Body Beginner");
                t1.setDescription("A simple full body routine for beginners.");
                t1.setExercises(Arrays.asList("Squats", "Push-ups", "Pull-ups", "Plank"));
                repo.save(t1);

                TrainingPlanTemplate t2 = new TrainingPlanTemplate();
                t2.setName("Upper/Lower Split");
                t2.setDescription("Alternating upper and lower body workouts.");
                t2.setExercises(Arrays.asList("Bench Press", "Rows", "Shoulder Press", "Squats", "Deadlifts", "Lunges"));
                repo.save(t2);

                TrainingPlanTemplate t3 = new TrainingPlanTemplate();
                t3.setName("Push/Pull/Legs");
                t3.setDescription("Classic push, pull, and legs split.");
                t3.setExercises(Arrays.asList("Bench Press", "Overhead Press", "Rows", "Pull-ups", "Squats", "Leg Press"));
                repo.save(t3);

                TrainingPlanTemplate t4 = new TrainingPlanTemplate();
                t4.setName("Bodyweight Circuit");
                t4.setDescription("No equipment, all bodyweight movements.");
                t4.setExercises(Arrays.asList("Push-ups", "Squats", "Lunges", "Plank", "Burpees"));
                repo.save(t4);
            }
        };
    }
}
