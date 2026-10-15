package com.eventmgmt.repository;

import com.eventmgmt.model.Event;
import com.eventmgmt.model.Registration;
import com.eventmgmt.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    List<Registration> findByAttendeeOrderByCreatedAtDesc(User attendee);
    List<Registration> findByEventOrderByCreatedAtAsc(Event event);
    List<Registration> findByEventIn(List<Event> events);
    Optional<Registration> findByEventAndAttendee(Event event, User attendee);
    boolean existsByEventAndAttendee(Event event, User attendee);
    long countByEvent(Event event);
}
