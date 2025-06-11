package com.gymtracker.backend.repository;

import com.gymtracker.backend.model.TrainingSession;
import com.gymtracker.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    List<TrainingSession> findByUserOrderByDateAsc(User user);
    List<TrainingSession> findByUserOrderByDateDesc(User user);
    List<TrainingSession> findByUserAndDateAfterOrderByDateDesc(User user, LocalDate date);
    List<TrainingSession> findByUserAndDateBetweenOrderByDateDesc(User user, LocalDate startDate, LocalDate endDate);
}
