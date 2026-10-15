package com.eventmgmt.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_orders")
public class PaymentOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 100) private String razorpayOrderId;
    @ManyToOne(optional = false) private Ticket ticket;
    @ManyToOne(optional = false) private User attendee;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false) private long amountPaise;
    @Column(nullable = false, length = 20) private String status = "PENDING";
    @Column(length = 100) private String razorpayPaymentId;
    @Column(length = 40) private String paymentMethod = "RAZORPAY";
    @Column(length = 80, unique = true) private String upiTransactionId;
    @Column(length = 100) private String ticketReference;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime completedAt;
    public Long getId(){return id;} public String getRazorpayOrderId(){return razorpayOrderId;} public void setRazorpayOrderId(String v){razorpayOrderId=v;}
    public Ticket getTicket(){return ticket;} public void setTicket(Ticket v){ticket=v;} public User getAttendee(){return attendee;} public void setAttendee(User v){attendee=v;}
    public int getQuantity(){return quantity;} public void setQuantity(int v){quantity=v;} public long getAmountPaise(){return amountPaise;} public void setAmountPaise(long v){amountPaise=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getRazorpayPaymentId(){return razorpayPaymentId;} public void setRazorpayPaymentId(String v){razorpayPaymentId=v;}
    public String getPaymentMethod(){return paymentMethod;} public void setPaymentMethod(String v){paymentMethod=v;} public String getUpiTransactionId(){return upiTransactionId;} public void setUpiTransactionId(String v){upiTransactionId=v;}
    public String getTicketReference(){return ticketReference;} public void setTicketReference(String v){ticketReference=v;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
}
