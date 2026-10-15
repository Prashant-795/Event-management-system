package com.eventmgmt.service;

import com.eventmgmt.model.ActivityLog;
import com.eventmgmt.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {

    private final ActivityLogRepository repo;

    public ActivityService(ActivityLogRepository repo) {
        this.repo = repo;
    }

    public void log(String message) {
        repo.save(new ActivityLog(message));
    }
}
