package com.eventmgmt.controller;

import com.eventmgmt.dto.RegisterForm;
import com.eventmgmt.model.*;
import com.eventmgmt.repository.*;
import com.eventmgmt.service.SettingService;
import com.eventmgmt.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Controller
public class PublicController {

    private final EventRepository events;
    private final TicketRepository tickets;
    private final RegistrationRepository registrations;
    private final UserRepository users;
    private final UserService userService;
    private final SettingService settings;

    public PublicController(EventRepository events, TicketRepository tickets, RegistrationRepository registrations,
                            UserRepository users, UserService userService, SettingService settings) {
        this.events = events;
        this.tickets = tickets;
        this.registrations = registrations;
        this.users = users;
        this.userService = userService;
        this.settings = settings;
    }

    private boolean isLoggedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String q, Model model) {
        List<Event> upcoming = events.findByStatusAndEventDateAfterOrderByEventDateAsc(EventStatus.APPROVED, LocalDateTime.now());
        if (q != null && !q.isBlank()) {
            String needle = q.trim().toLowerCase(Locale.ROOT);
            upcoming = upcoming.stream()
                    .filter(e -> e.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                            || e.getVenue().toLowerCase(Locale.ROOT).contains(needle)
                            || e.getDescription().toLowerCase(Locale.ROOT).contains(needle))
                    .toList();
        }
        model.addAttribute("events", upcoming);
        model.addAttribute("q", q);
        return "index";
    }

    @GetMapping("/events/{id}")
    public String eventDetail(@PathVariable Long id, Authentication auth, Model model) {
        Event e = events.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User me = isLoggedIn(auth) ? users.findByEmail(auth.getName()).orElse(null) : null;

        boolean canSee = e.getStatus() == EventStatus.APPROVED
                || (me != null && (me.getRole() == Role.ADMIN || e.getOrganizer().getId().equals(me.getId())));
        if (!canSee) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        model.addAttribute("event", e);
        model.addAttribute("tickets", tickets.findByEventOrderByPriceAsc(e));
        model.addAttribute("registeredCount", registrations.countByEvent(e));
        model.addAttribute("isAttendee", me != null && me.getRole() == Role.ATTENDEE);
        model.addAttribute("registered", me != null && registrations.existsByEventAndAttendee(e, me));
        model.addAttribute("past", e.getEventDate().isBefore(LocalDateTime.now()));
        return "event-detail";
    }

    @GetMapping("/login")
    public String login(Authentication auth) {
        return isLoggedIn(auth) ? "redirect:/dashboard" : "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterForm());
        model.addAttribute("open", settings.getBoolean("registration.open", true));
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult br, Model model) {
        boolean open = settings.getBoolean("registration.open", true);
        model.addAttribute("open", open);
        if (!open) {
            br.reject("closed", "New registrations are currently closed");
            return "register";
        }
        Role role;
        try {
            role = Role.valueOf(form.getRole());
        } catch (IllegalArgumentException ex) {
            role = Role.ATTENDEE;
        }
        if (role == Role.ADMIN) role = Role.ATTENDEE; // never allow self-signup as admin
        if (br.hasErrors()) return "register";
        try {
            userService.create(form.getName(), form.getEmail(), form.getPassword(), role);
        } catch (IllegalArgumentException ex) {
            br.rejectValue("email", "duplicate", ex.getMessage());
            return "register";
        }
        return "redirect:/login?registered";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth) {
        for (var a : auth.getAuthorities()) {
            switch (a.getAuthority()) {
                case "ROLE_ADMIN": return "redirect:/admin/dashboard";
                case "ROLE_ORGANIZER": return "redirect:/organizer/dashboard";
                case "ROLE_ATTENDEE": return "redirect:/attendee/dashboard";
                default: break;
            }
        }
        return "redirect:/";
    }
}
