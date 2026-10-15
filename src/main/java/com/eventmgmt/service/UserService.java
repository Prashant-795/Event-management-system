package com.eventmgmt.service;

import com.eventmgmt.model.*;
import com.eventmgmt.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository users;
    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final TicketPurchaseRepository purchases;
    private final PasswordEncoder encoder;
    private final ActivityService activity;

    public UserService(UserRepository users, EventRepository events, RegistrationRepository registrations,
                       TicketPurchaseRepository purchases, PasswordEncoder encoder, ActivityService activity) {
        this.users = users;
        this.events = events;
        this.registrations = registrations;
        this.purchases = purchases;
        this.encoder = encoder;
        this.activity = activity;
    }

    public User findByEmail(String email) {
        return users.findByEmail(email).orElseThrow(() -> new IllegalStateException("User not found"));
    }

    @Transactional
    public User save(User user) { return users.save(user); }

    @Transactional
    public User create(String name, String email, String rawPassword, Role role) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        User u = new User();
        u.setName(name.trim());
        u.setEmail(normalized);
        u.setPassword(encoder.encode(rawPassword));
        u.setRole(role);
        User saved = users.save(u);
        activity.log("New " + role + " account created: " + normalized);
        return saved;
    }

    @Transactional
    public void adminUpdate(Long id, String name, String email, Role role, boolean enabled, String newPassword) {
        User u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        String normalized = email.trim().toLowerCase();
        if (!normalized.equals(u.getEmail()) && users.existsByEmail(normalized)) {
            throw new IllegalArgumentException("That email is already used by another account");
        }
        u.setName(name.trim());
        u.setEmail(normalized);
        u.setRole(role);
        u.setEnabled(enabled);
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters");
            u.setPassword(encoder.encode(newPassword));
        }
        users.save(u);
        activity.log("Admin updated user: " + normalized);
    }

    @Transactional
    public void updateProfile(User u, String name, String phone, String city, String preferences, String newPassword) {
        u.setName(name.trim());
        u.setPhone(phone);
        u.setCity(city);
        u.setPreferences(preferences);
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters");
            u.setPassword(encoder.encode(newPassword));
        }
        users.save(u);
    }

    @Transactional
    public void delete(Long id, String currentAdminEmail) {
        User u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (u.getEmail().equals(currentAdminEmail)) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        if (u.getRole() == Role.ORGANIZER) {
            events.deleteAll(events.findByOrganizerOrderByEventDateDesc(u));
        }
        if (u.getRole() == Role.ATTENDEE) {
            purchases.deleteAll(purchases.findByAttendeeOrderByPurchasedAtDesc(u));
            registrations.deleteAll(registrations.findByAttendeeOrderByCreatedAtDesc(u));
        }
        users.delete(u);
        activity.log("Admin deleted user: " + u.getEmail());
    }
}
