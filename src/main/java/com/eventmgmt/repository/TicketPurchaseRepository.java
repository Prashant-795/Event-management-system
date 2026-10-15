package com.eventmgmt.repository;

import com.eventmgmt.model.Ticket;
import com.eventmgmt.model.TicketPurchase;
import com.eventmgmt.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketPurchaseRepository extends JpaRepository<TicketPurchase, Long> {
    List<TicketPurchase> findByAttendeeOrderByPurchasedAtDesc(User attendee);
    List<TicketPurchase> findByTicketIn(List<Ticket> tickets);
    java.util.Optional<TicketPurchase> findByReference(String reference);
}
