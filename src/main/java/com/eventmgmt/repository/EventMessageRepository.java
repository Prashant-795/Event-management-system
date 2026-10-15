package com.eventmgmt.repository;

import com.eventmgmt.model.Event;
import com.eventmgmt.model.EventMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventMessageRepository extends JpaRepository<EventMessage, Long> {
    List<EventMessage> findByEventOrderByCreatedAtDesc(Event event);
    List<EventMessage> findByEventInOrderByCreatedAtDesc(List<Event> events);
}
