package com.eventmgmt.repository;

import com.eventmgmt.model.Event;
import com.eventmgmt.model.EventStatus;
import com.eventmgmt.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStatusOrderByEventDateAsc(EventStatus status);
    List<Event> findByStatusAndEventDateAfterOrderByEventDateAsc(EventStatus status, LocalDateTime after);
    List<Event> findByOrganizerOrderByEventDateDesc(User organizer);
    List<Event> findAllByOrderByCreatedAtDesc();
    long countByStatus(EventStatus status);
}
