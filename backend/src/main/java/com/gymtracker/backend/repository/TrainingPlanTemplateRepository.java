package com.gymtracker.backend.repository;

import com.gymtracker.backend.model.TrainingPlanTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingPlanTemplateRepository extends JpaRepository<TrainingPlanTemplate, Long> {
}
