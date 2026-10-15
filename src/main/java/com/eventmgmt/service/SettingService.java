package com.eventmgmt.service;

import com.eventmgmt.model.Setting;
import com.eventmgmt.repository.SettingRepository;
import org.springframework.stereotype.Service;

@Service
public class SettingService {

    private final SettingRepository repo;

    public SettingService(SettingRepository repo) {
        this.repo = repo;
    }

    public String get(String key, String fallback) {
        return repo.findById(key).map(Setting::getValue).orElse(fallback);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
    }

    public void set(String key, String value) {
        repo.save(new Setting(key, value));
    }

    public void setDefault(String key, String value) {
        if (!repo.existsById(key)) {
            repo.save(new Setting(key, value));
        }
    }
}
