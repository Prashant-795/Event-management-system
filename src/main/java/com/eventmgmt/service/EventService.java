package com.eventmgmt.service;

import com.eventmgmt.dto.EventForm;
import com.eventmgmt.model.*;
import com.eventmgmt.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository events;
    private final TicketRepository tickets;
    private final TicketPurchaseRepository purchases;
    private final RegistrationRepository registrations;
    private final EventMessageRepository messages;
    private final SettingService settings;
    private final ActivityService activity;

    public EventService(EventRepository events, TicketRepository tickets, TicketPurchaseRepository purchases,
                        RegistrationRepository registrations, EventMessageRepository messages,
                        SettingService settings, ActivityService activity) {
        this.events = events;
        this.tickets = tickets;
        this.purchases = purchases;
        this.registrations = registrations;
        this.messages = messages;
        this.settings = settings;
        this.activity = activity;
    }

    // ---------- events ----------

    public Event getOwned(Long eventId, User organizer) {
        Event e = events.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        if (!e.getOrganizer().getId().equals(organizer.getId())) {
            throw new IllegalArgumentException("You do not own this event");
        }
        return e;
    }

    @Transactional
    public Event create(EventForm form, User organizer) {
        Event e = new Event();
        apply(e, form);
        e.setOrganizer(organizer);
        e.setStatus(settings.getBoolean("events.autoApprove", false) ? EventStatus.APPROVED : EventStatus.PENDING);
        Event saved = events.save(e);
        activity.log(organizer.getName() + " created event \"" + saved.getTitle() + "\" (" + saved.getStatus() + ")");
        return saved;
    }

    @Transactional
    public void update(Long eventId, EventForm form, User organizer) {
        Event e = getOwned(eventId, organizer);
        apply(e, form);
        // edited events go back through approval unless auto-approve is on
        e.setStatus(settings.getBoolean("events.autoApprove", false) ? EventStatus.APPROVED : EventStatus.PENDING);
        events.save(e);
        activity.log(organizer.getName() + " updated event \"" + e.getTitle() + "\"");
    }

    @Transactional
    public void delete(Long eventId, User organizer) {
        Event e = getOwned(eventId, organizer);
        events.delete(e);
        activity.log(organizer.getName() + " deleted event \"" + e.getTitle() + "\"");
    }

    @Transactional
    public void setStatus(Long eventId, EventStatus status) {
        Event e = events.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        e.setStatus(status);
        events.save(e);
        activity.log("Admin marked event \"" + e.getTitle() + "\" as " + status);
    }

    @Transactional
    public void adminDelete(Long eventId) {
        Event e = events.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        events.delete(e);
        activity.log("Admin deleted event \"" + e.getTitle() + "\"");
    }

    private void apply(Event e, EventForm f) {
        e.setTitle(f.getTitle().trim());
        e.setDescription(f.getDescription().trim());
        e.setEventDate(f.getEventDate());
        e.setVenue(f.getVenue().trim());
    }

    // ---------- tickets ----------

    @Transactional
    public void addTicket(Long eventId, User organizer, String name, BigDecimal price, int quantity) {
        Event e = getOwned(eventId, organizer);
        validateTicket(name, price, quantity);
        Ticket t = new Ticket();
        t.setEvent(e);
        t.setName(name.trim());
        t.setPrice(price);
        t.setQuantity(quantity);
        tickets.save(t);
        activity.log("Ticket type \"" + t.getName() + "\" added to \"" + e.getTitle() + "\"");
    }

    @Transactional
    public void updateTicket(Long ticketId, User organizer, String name, BigDecimal price, int quantity) {
        Ticket t = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        getOwned(t.getEvent().getId(), organizer);
        validateTicket(name, price, quantity);
        if (quantity < t.getSold() + t.getReserved()) {
            throw new IllegalArgumentException("Quantity cannot be lower than sold plus reserved tickets (" + (t.getSold() + t.getReserved()) + ")");
        }
        t.setName(name.trim());
        t.setPrice(price);
        t.setQuantity(quantity);
        tickets.save(t);
    }

    @Transactional
    public Long deleteTicket(Long ticketId, User organizer) {
        Ticket t = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        Event e = getOwned(t.getEvent().getId(), organizer);
        if (t.getSold() > 0 || t.getReserved() > 0) {
            throw new IllegalArgumentException("Cannot delete a ticket type that has sales or pending checkout reservations");
        }
        tickets.delete(t);
        return e.getId();
    }

    private void validateTicket(String name, BigDecimal price, int quantity) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Ticket name is required");
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("Price must be greater than zero");
        if (price.scale() > 2) throw new IllegalArgumentException("Price can have at most two decimal places");
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1");
    }

    // ---------- attendees ----------

    @Transactional
    public void register(Long eventId, User attendee) {
        Event e = events.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        if (e.getStatus() != EventStatus.APPROVED) {
            throw new IllegalArgumentException("This event is not open for registration");
        }
        if (registrations.existsByEventAndAttendee(e, attendee)) {
            throw new IllegalArgumentException("You are already registered for this event");
        }
        Registration r = new Registration();
        r.setEvent(e);
        r.setAttendee(attendee);
        registrations.save(r);
        activity.log(attendee.getName() + " registered for \"" + e.getTitle() + "\"");
    }

    @Transactional
    public void unregister(Long eventId, User attendee) {
        Event e = events.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        registrations.findByEventAndAttendee(e, attendee).ifPresent(registrations::delete);
    }

    /** Legacy simulated purchase path intentionally disabled. Real purchases must use PaymentService. */
    @Deprecated
    public TicketPurchase purchase(Long ticketId, User attendee, int qty, String paymentMethod, String payerName) {
        throw new IllegalStateException("Simulated payments are disabled. Use the verified Razorpay checkout flow.");
    }

    // ---------- communication ----------

    @Transactional
    public void sendMessage(Long eventId, User organizer, String content) {
        Event e = getOwned(eventId, organizer);
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Message cannot be empty");
        if (content.length() > 1000) throw new IllegalArgumentException("Message is too long (max 1000 characters)");
        EventMessage m = new EventMessage();
        m.setEvent(e);
        m.setSender(organizer);
        m.setContent(content.trim());
        messages.save(m);
        activity.log(organizer.getName() + " sent an update for \"" + e.getTitle() + "\"");
    }
}
