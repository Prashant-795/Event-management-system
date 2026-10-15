package com.eventmgmt.config;

import com.eventmgmt.model.*;
import com.eventmgmt.repository.*;
import com.eventmgmt.service.SettingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Seeds default settings, demo accounts and one sample event on first start. */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository users;
    private final EventRepository events;
    private final TicketRepository tickets;
    private final SettingService settings;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, EventRepository events, TicketRepository tickets,
                           SettingService settings, PasswordEncoder encoder) {
        this.users = users;
        this.events = events;
        this.tickets = tickets;
        this.settings = settings;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        settings.setDefault("site.name", "EventHub");
        settings.setDefault("registration.open", "true");
        settings.setDefault("events.autoApprove", "false");
        settings.setDefault("support.email", "support@eventhub.local");

        if (users.count() > 0) return;

        User admin = createUser("Admin", "admin@eventhub.com", "admin123", Role.ADMIN);
        User organizer = createUser("Olivia Organizer", "organizer@eventhub.com", "organizer123", Role.ORGANIZER);
        createUser("Aarav Attendee", "attendee@eventhub.com", "attendee123", Role.ATTENDEE);

        Event e = new Event();
        e.setTitle("Java & Spring Boot Workshop");
        e.setDescription("A hands-on one-day workshop building a full-stack web application with Spring Boot.");
        e.setEventDate(LocalDateTime.now().plusDays(14).withHour(10).withMinute(0).withSecond(0).withNano(0));
        e.setVenue("Tech Hub Auditorium, Delhi NCR");
        e.setStatus(EventStatus.APPROVED);
        e.setOrganizer(organizer);
        events.save(e);

        Ticket general = new Ticket();
        general.setEvent(e);
        general.setName("General Admission");
        general.setPrice(new BigDecimal("499.00"));
        general.setQuantity(100);
        tickets.save(general);

        Ticket vip = new Ticket();
        vip.setEvent(e);
        vip.setName("VIP");
        vip.setPrice(new BigDecimal("1499.00"));
        vip.setQuantity(20);
        tickets.save(vip);
    }

    private User createUser(String name, String email, String rawPassword, Role role) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPassword(encoder.encode(rawPassword));
        u.setRole(role);
        return users.save(u);
    }
}
