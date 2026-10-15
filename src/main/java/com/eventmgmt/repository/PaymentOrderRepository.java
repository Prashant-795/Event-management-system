package com.eventmgmt.repository;

import com.eventmgmt.model.PaymentOrder;
import com.eventmgmt.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PaymentOrder p where p.razorpayOrderId = :orderId")
    Optional<PaymentOrder> findByRazorpayOrderIdForUpdate(@Param("orderId") String orderId);
    List<PaymentOrder> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);
    boolean existsByUpiTransactionIdIgnoreCase(String upiTransactionId);
    List<PaymentOrder> findByAttendeeAndStatusInOrderByCreatedAtDesc(User attendee, List<String> statuses);
    @org.springframework.data.jpa.repository.Query("select p from PaymentOrder p where p.status = 'UPI_REVIEW' and p.ticket.event.organizer = :organizer order by p.createdAt asc")
    List<PaymentOrder> findDirectUpiPaymentsForOrganizer(@Param("organizer") User organizer);
}
