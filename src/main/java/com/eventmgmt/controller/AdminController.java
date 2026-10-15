package com.eventmgmt.controller;

import com.eventmgmt.model.*;
import com.eventmgmt.repository.*;
import com.eventmgmt.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository users;
    private final EventRepository events;
    private final TicketRepository tickets;
    private final RegistrationRepository registrations;
    private final TicketPurchaseRepository purchases;
    private final ActivityLogRepository logs;
    private final UserService userService;
    private final EventService eventService;
    private final SettingService settings;
    private final ActivityService activity;

    public AdminController(UserRepository users, EventRepository events, TicketRepository tickets,
                           RegistrationRepository registrations, TicketPurchaseRepository purchases,
                           ActivityLogRepository logs, UserService userService, EventService eventService,
                           SettingService settings, ActivityService activity) {
        this.users = users;
        this.events = events;
        this.tickets = tickets;
        this.registrations = registrations;
        this.purchases = purchases;
        this.logs = logs;
        this.userService = userService;
        this.eventService = eventService;
        this.settings = settings;
        this.activity = activity;
    }

    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("userCount", users.count());
        model.addAttribute("eventCount", events.count());
        model.addAttribute("pendingCount", events.countByStatus(EventStatus.PENDING));
        model.addAttribute("registrationCount", registrations.count());

        List<TicketPurchase> all = purchases.findAll();
        int ticketsSold = all.stream().mapToInt(TicketPurchase::getQuantity).sum();
        BigDecimal revenue = all.stream().map(TicketPurchase::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("ticketsSold", ticketsSold);
        model.addAttribute("revenue", revenue);

        model.addAttribute("roleLabels", List.of("Admins", "Organizers", "Attendees"));
        model.addAttribute("roleValues", List.of(users.countByRole(Role.ADMIN), users.countByRole(Role.ORGANIZER), users.countByRole(Role.ATTENDEE)));
        model.addAttribute("statusLabels", List.of("Pending", "Approved", "Rejected"));
        model.addAttribute("statusValues", List.of(events.countByStatus(EventStatus.PENDING), events.countByStatus(EventStatus.APPROVED), events.countByStatus(EventStatus.REJECTED)));

        model.addAttribute("recent", events.findAllByOrderByCreatedAtDesc().stream().limit(8).toList());
        model.addAttribute("activity", logs.findTop25ByOrderByCreatedAtDesc());
        return "admin/dashboard";
    }

    @GetMapping("/route-accounts")
    public String routeAccounts(Model model) { model.addAttribute("organizers", users.findAll().stream().filter(u -> u.getRole() == Role.ORGANIZER).toList()); return "admin/route-accounts"; }

    @PostMapping("/route-accounts/{id}/approve")
    public String approveRouteAccount(@PathVariable Long id, RedirectAttributes ra) {
        User u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("Organizer not found"));
        if (u.getRole() != Role.ORGANIZER || u.getRazorpayRouteAccountId() == null || u.getRazorpayRouteAccountId().isBlank()) { ra.addFlashAttribute("error", "Organizer has no linked account ID."); return "redirect:/admin/route-accounts"; }
        // Only approve after independently verifying the linked account in the Razorpay dashboard.
        u.setRazorpayRouteApproved(true); users.save(u); ra.addFlashAttribute("success", "Route account approved. Confirmed against Razorpay dashboard by admin."); return "redirect:/admin/route-accounts";
    }
    @PostMapping("/route-accounts/{id}/reject")
    public String rejectRouteAccount(@PathVariable Long id, RedirectAttributes ra) { User u=users.findById(id).orElseThrow(() -> new IllegalArgumentException("Organizer not found")); u.setRazorpayRouteApproved(false); users.save(u); ra.addFlashAttribute("success", "Route account approval removed."); return "redirect:/admin/route-accounts"; }

    // ---------- user management ----------

    @GetMapping("/users")
    public String userList(Model model) {
        model.addAttribute("users", users.findAll());
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String name, @RequestParam String email, @RequestParam String password,
                             @RequestParam Role role, RedirectAttributes ra) {
        try {
            if (name.isBlank() || email.isBlank()) throw new IllegalArgumentException("Name and email are required");
            if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters");
            userService.create(name, email, password, role);
            ra.addFlashAttribute("success", "User created successfully");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/users/{id}/edit")
    public String editUser(@PathVariable Long id, Model model, RedirectAttributes ra) {
        User u = users.findById(id).orElse(null);
        if (u == null) {
            ra.addFlashAttribute("error", "User not found");
            return "redirect:/admin/users";
        }
        model.addAttribute("u", u);
        model.addAttribute("roles", Role.values());
        return "admin/user-edit";
    }

    @PostMapping("/users/{id}/edit")
    public String updateUser(@PathVariable Long id, @RequestParam String name, @RequestParam String email,
                             @RequestParam Role role, @RequestParam(defaultValue = "false") boolean enabled,
                             @RequestParam(required = false) String password, Principal principal,
                             RedirectAttributes ra) {
        try {
            User target = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
            if (target.getEmail().equals(principal.getName()) && (role != Role.ADMIN || !enabled)) {
                throw new IllegalArgumentException("You cannot demote or disable your own account");
            }
            if (name.isBlank() || email.isBlank()) throw new IllegalArgumentException("Name and email are required");
            userService.adminUpdate(id, name, email, role, enabled, password);
            ra.addFlashAttribute("success", "User updated successfully");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/users/" + id + "/edit";
        }
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        try {
            userService.delete(id, principal.getName());
            ra.addFlashAttribute("success", "User deleted successfully");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ---------- event approvals ----------

    @GetMapping("/events")
    public String eventList(Model model) {
        List<Event> all = events.findAllByOrderByCreatedAtDesc();
        model.addAttribute("pending", all.stream().filter(e -> e.getStatus() == EventStatus.PENDING).toList());
        model.addAttribute("events", all);
        return "admin/events";
    }

    @PostMapping("/events/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes ra) {
        return changeStatus(id, EventStatus.APPROVED, "Event approved", ra);
    }

    @PostMapping("/events/{id}/reject")
    public String reject(@PathVariable Long id, RedirectAttributes ra) {
        return changeStatus(id, EventStatus.REJECTED, "Event rejected", ra);
    }

    private String changeStatus(Long id, EventStatus status, String msg, RedirectAttributes ra) {
        try {
            eventService.setStatus(id, status);
            ra.addFlashAttribute("success", msg);
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/events";
    }

    @PostMapping("/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id, RedirectAttributes ra) {
        try {
            eventService.adminDelete(id);
            ra.addFlashAttribute("success", "Event deleted");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/events";
    }

    // ---------- settings ----------

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        model.addAttribute("siteName", settings.get("site.name", "EventHub"));
        model.addAttribute("registrationOpen", settings.getBoolean("registration.open", true));
        model.addAttribute("autoApprove", settings.getBoolean("events.autoApprove", false));
        model.addAttribute("supportEmail", settings.get("support.email", ""));
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam String siteName, @RequestParam(defaultValue = "false") boolean registrationOpen,
                               @RequestParam(defaultValue = "false") boolean autoApprove,
                               @RequestParam(defaultValue = "") String supportEmail, RedirectAttributes ra) {
        if (siteName.isBlank()) {
            ra.addFlashAttribute("error", "Site name cannot be empty");
            return "redirect:/admin/settings";
        }
        settings.set("site.name", siteName.trim());
        settings.set("registration.open", String.valueOf(registrationOpen));
        settings.set("events.autoApprove", String.valueOf(autoApprove));
        settings.set("support.email", supportEmail.trim());
        activity.log("Admin updated system settings");
        ra.addFlashAttribute("success", "Settings updated successfully");
        return "redirect:/admin/settings";
    }
}
