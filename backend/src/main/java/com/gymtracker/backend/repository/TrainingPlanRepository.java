package com.gymtracker.backend.repository;

import com.gymtracker.backend.model.TrainingPlan;
import com.gymtracker.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {
    List<TrainingPlan> findByUser(User user);
}
