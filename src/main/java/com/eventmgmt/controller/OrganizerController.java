package com.eventmgmt.controller;

import com.eventmgmt.dto.EventForm;
import com.eventmgmt.model.*;
import com.eventmgmt.repository.*;
import com.eventmgmt.service.EventService;
import com.eventmgmt.service.UserService;
import com.eventmgmt.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/organizer")
public class OrganizerController {

    private final EventRepository events;
    private final TicketRepository tickets;
    private final RegistrationRepository registrations;
    private final TicketPurchaseRepository purchases;
    private final EventMessageRepository messages;
    private final EventService eventService;
    private final UserService userService;
    private final PaymentOrderRepository paymentOrders;
    private final PaymentService paymentService;

    public OrganizerController(EventRepository events, TicketRepository tickets, RegistrationRepository registrations,
                               TicketPurchaseRepository purchases, EventMessageRepository messages,
                               EventService eventService, UserService userService, PaymentOrderRepository paymentOrders, PaymentService paymentService) {
        this.events = events;
        this.tickets = tickets;
        this.registrations = registrations;
        this.purchases = purchases;
        this.messages = messages;
        this.eventService = eventService;
        this.userService = userService;
        this.paymentOrders = paymentOrders;
        this.paymentService = paymentService;
    }

    private User me(Principal p) {
        return userService.findByEmail(p.getName());
    }

    @GetMapping({"", "/dashboard"})
    public String dashboard(Principal principal, Model model) {
        User organizer = me(principal);
        List<Event> mine = events.findByOrganizerOrderByEventDateDesc(organizer);

        List<String> labels = new ArrayList<>();
        List<Long> regCounts = new ArrayList<>();
        List<Integer> soldCounts = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO;
        Map<String, Long> byCity = new TreeMap<>();

        for (Event e : mine) {
            List<Registration> regs = registrations.findByEventOrderByCreatedAtAsc(e);
            labels.add(e.getTitle().length() > 22 ? e.getTitle().substring(0, 22) + "…" : e.getTitle());
            regCounts.add((long) regs.size());
            int sold = e.getTickets().stream().mapToInt(Ticket::getSold).sum();
            soldCounts.add(sold);
            for (Registration r : regs) {
                String city = r.getAttendee().getCity();
                byCity.merge(city == null || city.isBlank() ? "Not specified" : city.trim(), 1L, Long::sum);
            }
        }
        List<Ticket> allTickets = mine.stream().flatMap(e -> e.getTickets().stream()).toList();
        if (!allTickets.isEmpty()) {
            for (TicketPurchase p : purchases.findByTicketIn(allTickets)) {
                revenue = revenue.add(p.getTotalAmount());
            }
        }

        model.addAttribute("events", mine);
        model.addAttribute("upcoming", mine.stream()
                .filter(e -> e.getStatus() == EventStatus.APPROVED && e.getEventDate().isAfter(LocalDateTime.now()))
                .sorted(Comparator.comparing(Event::getEventDate)).toList());
        model.addAttribute("labels", labels);
        model.addAttribute("regCounts", regCounts);
        model.addAttribute("soldCounts", soldCounts);
        model.addAttribute("cityLabels", new ArrayList<>(byCity.keySet()));
        model.addAttribute("cityValues", new ArrayList<>(byCity.values()));
        model.addAttribute("revenue", revenue);
        model.addAttribute("totalRegistrations", regCounts.stream().mapToLong(Long::longValue).sum());
        model.addAttribute("totalSold", soldCounts.stream().mapToInt(Integer::intValue).sum());
        model.addAttribute("now", LocalDateTime.now());
        return "organizer/dashboard";
    }

    @GetMapping("/payment-settings")
    public String paymentSettings(Principal principal, Model model) { model.addAttribute("organizer", me(principal)); return "organizer/payment-settings"; }

    @PostMapping("/payment-settings")
    public String savePaymentSettings(@RequestParam(defaultValue = "") String routeAccountId, @RequestParam(defaultValue = "") String upiId, Principal principal, RedirectAttributes ra) {
        User organizer = me(principal);
        String accountId = routeAccountId == null ? "" : routeAccountId.trim();
        if (!accountId.isEmpty() && !accountId.matches("acc_[A-Za-z0-9]+")) { ra.addFlashAttribute("error", "Enter a valid Razorpay Route linked account ID (starts with acc_)."); return "redirect:/organizer/payment-settings"; }
        String cleanUpiId = upiId == null ? "" : upiId.trim();
        if (!cleanUpiId.isEmpty() && !cleanUpiId.matches("(?i)[a-z0-9._-]{2,100}@[a-z0-9.-]{2,50}")) { ra.addFlashAttribute("error", "Enter a valid UPI ID, for example name@bank."); return "redirect:/organizer/payment-settings"; }
        if (!java.util.Objects.equals(organizer.getRazorpayRouteAccountId(), accountId)) organizer.setRazorpayRouteApproved(false);
        organizer.setRazorpayRouteAccountId(accountId.isEmpty() ? null : accountId);
        organizer.setUpiId(cleanUpiId.isEmpty() ? null : cleanUpiId);
        userService.save(organizer);
        ra.addFlashAttribute("success", "Payment settings saved. Direct UPI payments require you to verify each transaction in your bank/UPI app before approving tickets.");
        return "redirect:/organizer/payment-settings";
    }

    @GetMapping("/upi-payments")
    public String upiPayments(Principal principal, Model model) {
        model.addAttribute("payments", paymentOrders.findDirectUpiPaymentsForOrganizer(me(principal)));
        return "organizer/upi-payments";
    }

    @PostMapping("/upi-payments/{id}/approve")
    public String approveUpiPayment(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        try {
            paymentService.approveDirectUpiPayment(id, me(principal));
            ra.addFlashAttribute("success", "Payment marked verified and ticket issued.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/organizer/upi-payments";
    }

    @PostMapping("/upi-payments/{id}/reject")
    public String rejectUpiPayment(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        try {
            paymentService.rejectDirectUpiPayment(id, me(principal));
            ra.addFlashAttribute("success", "Payment submission rejected and ticket inventory released.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/organizer/upi-payments";
    }

    // ---------- event CRUD ----------

    @GetMapping("/events/new")
    public String newEvent(Model model) {
        model.addAttribute("form", new EventForm());
        model.addAttribute("editing", false);
        return "organizer/event-form";
    }

    @PostMapping("/events")
    public String createEvent(@Valid @ModelAttribute("form") EventForm form, BindingResult br, Principal principal,
                              Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            model.addAttribute("editing", false);
            return "organizer/event-form";
        }
        Event saved = eventService.create(form, me(principal));
        ra.addFlashAttribute("success", saved.getStatus() == EventStatus.APPROVED
                ? "Event created and published"
                : "Event created. It will go live once an admin approves it.");
        return "redirect:/organizer/events/" + saved.getId() + "/tickets";
    }

    @GetMapping("/events/{id}/edit")
    public String editEvent(@PathVariable Long id, Principal principal, Model model, RedirectAttributes ra) {
        try {
            Event e = eventService.getOwned(id, me(principal));
            EventForm f = new EventForm();
            f.setTitle(e.getTitle());
            f.setDescription(e.getDescription());
            f.setEventDate(e.getEventDate());
            f.setVenue(e.getVenue());
            model.addAttribute("form", f);
            model.addAttribute("editing", true);
            model.addAttribute("eventId", id);
            return "organizer/event-form";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/organizer/dashboard";
        }
    }

    @PostMapping("/events/{id}")
    public String updateEvent(@PathVariable Long id, @Valid @ModelAttribute("form") EventForm form, BindingResult br,
                              Principal principal, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            model.addAttribute("editing", true);
            model.addAttribute("eventId", id);
            return "organizer/event-form";
        }
        try {
            eventService.update(id, form, me(principal));
            ra.addFlashAttribute("success", "Event updated successfully");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/organizer/dashboard";
    }

    @PostMapping("/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        try {
            eventService.delete(id, me(principal));
            ra.addFlashAttribute("success", "Event deleted");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/organizer/dashboard";
    }

    // ---------- tickets ----------

    @GetMapping("/events/{id}/tickets")
    public String tickets(@PathVariable Long id, Principal principal, Model model, RedirectAttributes ra) {
        try {
            Event e = eventService.getOwned(id, me(principal));
            model.addAttribute("event", e);
            model.addAttribute("tickets", tickets.findByEventOrderByPriceAsc(e));
            return "organizer/tickets";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/organizer/dashboard";
        }
    }

    @PostMapping("/events/{id}/tickets")
    public String addTicket(@PathVariable Long id, @RequestParam String name, @RequestParam BigDecimal price,
                            @RequestParam int quantity, Principal principal, RedirectAttributes ra) {
        try {
            eventService.addTicket(id, me(principal), name, price, quantity);
            ra.addFlashAttribute("success", "Ticket type added successfully");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/organizer/events/" + id + "/tickets";
    }

    @PostMapping("/tickets/{ticketId}/update")
    public String updateTicket(@PathVariable Long ticketId, @RequestParam Long eventId, @RequestParam String name,
                               @RequestParam BigDecimal price, @RequestParam int quantity, Principal principal,
                               RedirectAttributes ra) {
        try {
            eventService.updateTicket(ticketId, me(principal), name, price, quantity);
            ra.addFlashAttribute("success", "Ticket updated successfully");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/organizer/events/" + eventId + "/tickets";
    }

    @PostMapping("/tickets/{ticketId}/delete")
    public String deleteTicket(@PathVariable Long ticketId, @RequestParam Long eventId, Principal principal,
                               RedirectAttributes ra) {
        try {
            eventService.deleteTicket(ticketId, me(principal));
            ra.addFlashAttribute("success", "Ticket type deleted");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/organizer/events/" + eventId + "/tickets";
    }

    // ---------- attendee communication ----------

    @GetMapping("/events/{id}/messages")
    public String messagesPage(@PathVariable Long id, Principal principal, Model model, RedirectAttributes ra) {
        try {
            Event e = eventService.getOwned(id, me(principal));
            model.addAttribute("event", e);
            model.addAttribute("registrations", registrations.findByEventOrderByCreatedAtAsc(e));
            model.addAttribute("messages", messages.findByEventOrderByCreatedAtDesc(e));
            return "organizer/messages";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/organizer/dashboard";
        }
    }

    @PostMapping("/events/{id}/messages")
    public String sendMessage(@PathVariable Long id, @RequestParam String content, Principal principal,
                              RedirectAttributes ra) {
        try {
            eventService.sendMessage(id, me(principal), content);
            ra.addFlashAttribute("success", "Message delivered to registered attendees");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/organizer/events/" + id + "/messages";
    }
}
