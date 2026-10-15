package com.eventmgmt.repository;

import com.eventmgmt.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findTop25ByOrderByCreatedAtDesc();
}
