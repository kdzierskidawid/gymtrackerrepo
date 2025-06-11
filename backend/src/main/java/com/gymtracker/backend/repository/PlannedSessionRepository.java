package com.gymtracker.backend.repository;

import com.gymtracker.backend.model.PlannedSession;
import com.gymtracker.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlannedSessionRepository extends JpaRepository<PlannedSession, Long> {
    List<PlannedSession> findByUserOrderByDateAsc(User user);
}
