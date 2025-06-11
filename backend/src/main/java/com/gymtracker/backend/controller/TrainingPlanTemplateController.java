package com.gymtracker.backend.controller;

import com.gymtracker.backend.model.TrainingPlanTemplate;
import com.gymtracker.backend.repository.TrainingPlanTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/training/templates")
public class TrainingPlanTemplateController {
    @Autowired private TrainingPlanTemplateRepository templateRepo;

    @GetMapping
    public List<TrainingPlanTemplate> getTemplates() {
        return templateRepo.findAll();
    }
}
