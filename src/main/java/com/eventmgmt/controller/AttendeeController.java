package com.eventmgmt.controller;

import com.eventmgmt.model.*;
import com.eventmgmt.repository.*;
import com.eventmgmt.service.EventService;
import com.eventmgmt.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/attendee")
public class AttendeeController {

    private final RegistrationRepository registrations;
    private final TicketPurchaseRepository purchases;
    private final EventMessageRepository messages;
    private final EventService eventService;
    private final UserService userService;
    private final PaymentOrderRepository paymentOrders;

    public AttendeeController(RegistrationRepository registrations, TicketPurchaseRepository purchases,
                              EventMessageRepository messages, EventService eventService, UserService userService, PaymentOrderRepository paymentOrders) {
        this.registrations = registrations;
        this.purchases = purchases;
        this.messages = messages;
        this.eventService = eventService;
        this.userService = userService;
        this.paymentOrders = paymentOrders;
    }

    private User me(Principal p) {
        return userService.findByEmail(p.getName());
    }

    @GetMapping({"", "/dashboard"})
    public String dashboard(Principal principal, Model model) {
        User u = me(principal);
        List<Registration> regs = registrations.findByAttendeeOrderByCreatedAtDesc(u);
        List<Registration> upcoming = regs.stream()
                .filter(r -> r.getEvent().getEventDate().isAfter(LocalDateTime.now()))
                .sorted((a, b) -> a.getEvent().getEventDate().compareTo(b.getEvent().getEventDate()))
                .toList();
        model.addAttribute("user", u);
        model.addAttribute("upcoming", upcoming);
        model.addAttribute("ticketCount", purchases.findByAttendeeOrderByPurchasedAtDesc(u).stream().mapToInt(TicketPurchase::getQuantity).sum());
        model.addAttribute("historyCount", regs.size());
        return "attendee/dashboard";
    }

    @PostMapping("/events/{id}/register")
    public String register(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        try {
            eventService.register(id, me(principal));
            ra.addFlashAttribute("success", "Registration confirmed! You will receive updates from the organizer.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/events/" + id;
    }

    @PostMapping("/events/{id}/unregister")
    public String unregister(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        try {
            eventService.unregister(id, me(principal));
            ra.addFlashAttribute("success", "Registration cancelled");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/events/" + id;
    }

    @PostMapping("/tickets/{ticketId}/buy")
    public String legacyBuyDisabled(@PathVariable Long ticketId, @RequestParam(required = false) Long eventId,
                                    RedirectAttributes ra) {
        ra.addFlashAttribute("error", "Simulated payments have been disabled. Please start checkout from the event page.");
        return "redirect:/events/" + (eventId == null ? "" : eventId);
    }

    @GetMapping("/tickets")
    public String myTickets(Principal principal, Model model) {
        User attendee = me(principal);
        model.addAttribute("purchases", purchases.findByAttendeeOrderByPurchasedAtDesc(attendee));
        model.addAttribute("pendingUpiPayments", paymentOrders.findByAttendeeAndStatusInOrderByCreatedAtDesc(attendee, List.of("UPI_AWAITING_PAYMENT", "UPI_REVIEW")));
        return "attendee/tickets";
    }

    @GetMapping("/updates")
    public String updates(Principal principal, Model model) {
        List<Event> myEvents = registrations.findByAttendeeOrderByCreatedAtDesc(me(principal))
                .stream().map(Registration::getEvent).toList();
        model.addAttribute("messages", myEvents.isEmpty() ? List.of() : messages.findByEventInOrderByCreatedAtDesc(myEvents));
        return "attendee/updates";
    }

    @GetMapping("/history")
    public String history(Principal principal, Model model) {
        model.addAttribute("registrations", registrations.findByAttendeeOrderByCreatedAtDesc(me(principal)));
        model.addAttribute("now", LocalDateTime.now());
        return "attendee/history";
    }

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        model.addAttribute("user", me(principal));
        return "attendee/profile";
    }

    @PostMapping("/profile")
    public String saveProfile(@RequestParam String name, @RequestParam(defaultValue = "") String phone,
                              @RequestParam(defaultValue = "") String city,
                              @RequestParam(defaultValue = "") String preferences,
                              @RequestParam(required = false) String password,
                              Principal principal, RedirectAttributes ra) {
        try {
            if (name.isBlank()) throw new IllegalArgumentException("Name is required");
            userService.updateProfile(me(principal), name, phone, city, preferences, password);
            ra.addFlashAttribute("success", "Profile updated successfully");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/attendee/profile";
    }
}
