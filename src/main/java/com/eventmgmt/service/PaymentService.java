package com.eventmgmt.service;

import com.eventmgmt.model.*;
import com.eventmgmt.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PaymentService {
    private final RazorpayGateway gateway; private final TicketRepository tickets; private final PaymentOrderRepository orders;
    private final TicketPurchaseRepository purchases; private final RegistrationRepository registrations; private final ActivityService activity;
    @Value("${razorpay.key-secret:}") private String keySecret;
    @Value("${razorpay.route-enabled:false}") private boolean routeEnabled;
    public PaymentService(RazorpayGateway gateway, TicketRepository tickets, PaymentOrderRepository orders, TicketPurchaseRepository purchases, RegistrationRepository registrations, ActivityService activity) {
        this.gateway=gateway; this.tickets=tickets; this.orders=orders; this.purchases=purchases; this.registrations=registrations; this.activity=activity;
    }
    @Transactional
    public PaymentOrder createOrder(Long ticketId, User attendee, int quantity) {
        gateway.requireConfigured();
        if (quantity < 1 || quantity > 10) throw new IllegalArgumentException("You can buy between 1 and 10 tickets at a time");
        Ticket t=tickets.findByIdForUpdate(ticketId).orElseThrow(()->new IllegalArgumentException("Ticket not found"));
        if (t.getEvent().getStatus()!=EventStatus.APPROVED || t.getEvent().getEventDate().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("Tickets for this event are not on sale");
        if (quantity > t.getAvailable()) throw new IllegalArgumentException("Only " + t.getAvailable() + " ticket(s) left");
        User organizer=t.getEvent().getOrganizer();
        if (!routeEnabled) throw new IllegalStateException("Organizer payouts are disabled until RAZORPAY_ROUTE_ENABLED=true and Route is approved for your account.");
        if (!organizer.isRazorpayRouteApproved() || organizer.getRazorpayRouteAccountId()==null || organizer.getRazorpayRouteAccountId().isBlank()) throw new IllegalStateException("This organizer has not completed Razorpay Route onboarding. Please contact the organizer.");
        long amount=t.getPrice().multiply(BigDecimal.valueOf(quantity)).movePointRight(2).longValueExact();
        if (amount < 100) throw new IllegalArgumentException("Razorpay requires a minimum order amount of ₹1.00");
        String receipt="evt-"+UUID.randomUUID().toString().replace("-", "").substring(0,20);
        Map<String,Object> transfer=new LinkedHashMap<>(); transfer.put("account",organizer.getRazorpayRouteAccountId()); transfer.put("amount",amount); transfer.put("currency","INR");
        Map<String,Object> body=new LinkedHashMap<>(); body.put("amount",amount); body.put("currency","INR"); body.put("receipt",receipt); body.put("expire_by", java.time.Instant.now().getEpochSecond()+900);
        body.put("transfers",List.of(transfer));
        Map<String,Object> response=gateway.createOrder(body);
        String orderId=Objects.toString(response.get("id"), ""); if(orderId.isBlank()) throw new IllegalStateException("Razorpay did not return an order ID");
        t.setReserved(t.getReserved()+quantity);
        PaymentOrder po=new PaymentOrder(); po.setRazorpayOrderId(orderId); po.setTicket(t); po.setAttendee(attendee); po.setQuantity(quantity); po.setAmountPaise(amount); po.setStatus("PENDING");
        return orders.save(po);
    }
    @Transactional
    public PaymentOrder createDirectUpiOrder(Long ticketId, User attendee, int quantity) {
        if (quantity < 1 || quantity > 10) throw new IllegalArgumentException("You can buy between 1 and 10 tickets at a time");
        Ticket t=tickets.findByIdForUpdate(ticketId).orElseThrow(()->new IllegalArgumentException("Ticket not found"));
        if (t.getEvent().getStatus()!=EventStatus.APPROVED || t.getEvent().getEventDate().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("Tickets for this event are not on sale");
        if (quantity > t.getAvailable()) throw new IllegalArgumentException("Only " + t.getAvailable() + " ticket(s) left");
        String upiId=t.getEvent().getOrganizer().getUpiId();
        if (upiId==null || !upiId.matches("(?i)[a-z0-9._-]{2,100}@[a-z0-9.-]{2,50}")) throw new IllegalStateException("This organizer has not configured a valid UPI ID yet");
        long amount=t.getPrice().multiply(BigDecimal.valueOf(quantity)).movePointRight(2).longValueExact();
        if (amount < 100) throw new IllegalArgumentException("The payment amount must be at least ₹1.00");
        t.setReserved(t.getReserved()+quantity);
        PaymentOrder po=new PaymentOrder();
        po.setRazorpayOrderId("UPI-"+UUID.randomUUID().toString().replace("-", "").substring(0,24).toUpperCase(Locale.ROOT));
        po.setTicket(t); po.setAttendee(attendee); po.setQuantity(quantity); po.setAmountPaise(amount);
        po.setPaymentMethod("UPI_DIRECT"); po.setStatus("UPI_AWAITING_PAYMENT");
        return orders.save(po);
    }

    @Transactional(readOnly = true)
    public PaymentOrder getDirectUpiOrder(Long id, User attendee) {
        PaymentOrder po=orders.findById(id).orElseThrow(()->new IllegalArgumentException("Payment request not found"));
        if (!po.getAttendee().getId().equals(attendee.getId()) || !"UPI_AWAITING_PAYMENT".equals(po.getStatus())) throw new IllegalArgumentException("This UPI payment request is no longer available");
        return po;
    }

    @Transactional
    public void submitDirectUpiReference(Long id, User attendee, String transactionId) {
        PaymentOrder po=orders.findById(id).orElseThrow(()->new IllegalArgumentException("Payment request not found"));
        if (!po.getAttendee().getId().equals(attendee.getId())) throw new IllegalArgumentException("This payment request belongs to another attendee");
        if (!"UPI_AWAITING_PAYMENT".equals(po.getStatus())) throw new IllegalArgumentException("This UPI payment request is no longer awaiting payment details");
        String utr=transactionId==null ? "" : transactionId.trim();
        if (!utr.matches("[A-Za-z0-9-]{6,80}")) throw new IllegalArgumentException("Enter a valid UPI transaction reference/UTR (6–80 letters or digits)");
        if (orders.existsByUpiTransactionIdIgnoreCase(utr)) throw new IllegalArgumentException("This UPI transaction reference has already been submitted");
        po.setUpiTransactionId(utr); po.setStatus("UPI_REVIEW"); orders.save(po);
        activity.log(attendee.getName()+" submitted a direct UPI payment reference for organizer review");
    }

    @Transactional
    public TicketPurchase approveDirectUpiPayment(Long paymentOrderId, User organizer) {
        PaymentOrder po=orders.findById(paymentOrderId).orElseThrow(()->new IllegalArgumentException("Payment request not found"));
        if (!po.getTicket().getEvent().getOrganizer().getId().equals(organizer.getId())) throw new IllegalArgumentException("You can only verify payments for your own events");
        if (!"UPI_REVIEW".equals(po.getStatus()) || po.getUpiTransactionId()==null || po.getUpiTransactionId().isBlank()) throw new IllegalArgumentException("This payment is not awaiting UPI verification");
        Ticket t=tickets.findByIdForUpdate(po.getTicket().getId()).orElseThrow(()->new IllegalArgumentException("Ticket not found"));
        if (t.getReserved()<po.getQuantity()) throw new IllegalStateException("Ticket reservation is inconsistent; contact support before approving");
        t.setReserved(t.getReserved()-po.getQuantity()); t.setSold(t.getSold()+po.getQuantity()); tickets.save(t);
        TicketPurchase purchase=new TicketPurchase(); purchase.setTicket(t); purchase.setAttendee(po.getAttendee()); purchase.setQuantity(po.getQuantity());
        purchase.setTotalAmount(BigDecimal.valueOf(po.getAmountPaise(),2)); purchase.setPaymentMethod("UPI_DIRECT");
        purchase.setReference("TKT-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT)); purchases.save(purchase);
        po.setStatus("PAID"); po.setTicketReference(purchase.getReference()); po.setCompletedAt(LocalDateTime.now()); orders.save(po);
        if(!registrations.existsByEventAndAttendee(t.getEvent(),po.getAttendee())){ Registration r=new Registration(); r.setEvent(t.getEvent()); r.setAttendee(po.getAttendee()); registrations.save(r); }
        activity.log(organizer.getName()+" verified direct UPI payment for "+po.getQuantity()+" x "+t.getName()+" (reference "+po.getUpiTransactionId()+")");
        return purchase;
    }

    @Transactional
    public void rejectDirectUpiPayment(Long paymentOrderId, User organizer) {
        PaymentOrder po=orders.findById(paymentOrderId).orElseThrow(()->new IllegalArgumentException("Payment request not found"));
        if (!po.getTicket().getEvent().getOrganizer().getId().equals(organizer.getId())) throw new IllegalArgumentException("You can only review payments for your own events");
        if (!"UPI_REVIEW".equals(po.getStatus())) throw new IllegalArgumentException("This payment is not awaiting review");
        Ticket t=tickets.findByIdForUpdate(po.getTicket().getId()).orElseThrow(()->new IllegalArgumentException("Ticket not found"));
        t.setReserved(Math.max(0,t.getReserved()-po.getQuantity())); tickets.save(t);
        po.setStatus("REJECTED"); orders.save(po);
        activity.log(organizer.getName()+" rejected a direct UPI payment reference for "+t.getName());
    }

    public boolean verifySignature(String orderId, String paymentId, String signature) {
        if(keySecret==null || keySecret.isBlank()) throw new IllegalStateException("Razorpay secret is not configured");
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] actual=mac.doFinal((orderId+"|"+paymentId).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(); for(byte b:actual) hex.append(String.format("%02x",b));
            return MessageDigest.isEqual(hex.toString().getBytes(StandardCharsets.US_ASCII), Objects.toString(signature,"").getBytes(StandardCharsets.US_ASCII));
        } catch(Exception e){throw new IllegalStateException("Unable to verify Razorpay signature",e);}
    }
    @Transactional
    public TicketPurchase verifyAndComplete(String orderId, String paymentId, String signature, User attendee) {
        PaymentOrder po=orders.findByRazorpayOrderIdForUpdate(orderId).orElseThrow(()->new IllegalArgumentException("Payment order not found"));
        if(!po.getAttendee().getId().equals(attendee.getId())) throw new IllegalArgumentException("This payment order belongs to another attendee");
        if("PAID".equals(po.getStatus())) return purchases.findByReference(po.getTicketReference()).orElseThrow(()->new IllegalStateException("Paid ticket record is missing"));
        if(!"PENDING".equals(po.getStatus())) throw new IllegalArgumentException("This payment order is no longer pending");
        if(!verifySignature(orderId,paymentId,signature)) throw new IllegalArgumentException("Razorpay payment signature is invalid");
        Map<String,Object> payment=gateway.fetchPayment(paymentId);
        return finalizeCapturedPayment(po, paymentId, payment);
    }
    @Transactional
    public void completeFromVerifiedWebhook(String orderId, String paymentId) {
        PaymentOrder po=orders.findByRazorpayOrderIdForUpdate(orderId).orElseThrow(()->new IllegalArgumentException("Payment order not found"));
        if("PAID".equals(po.getStatus())) return;
        if(!"PENDING".equals(po.getStatus())) throw new IllegalArgumentException("Webhook received for a non-pending order; reconcile manually");
        Map<String,Object> payment=gateway.fetchPayment(paymentId);
        finalizeCapturedPayment(po,paymentId,payment);
    }
    private TicketPurchase finalizeCapturedPayment(PaymentOrder po, String paymentId, Map<String,Object> payment) {
        String orderId=po.getRazorpayOrderId();
        if(!orderId.equals(Objects.toString(payment.get("order_id"),"")) || !paymentId.equals(Objects.toString(payment.get("id"),"")) || !"captured".equalsIgnoreCase(Objects.toString(payment.get("status"),"")) || number(payment.get("amount"))!=po.getAmountPaise() || !"INR".equals(Objects.toString(payment.get("currency"),""))) throw new IllegalArgumentException("Payment is not captured or does not match the expected order amount");
        Ticket t=tickets.findByIdForUpdate(po.getTicket().getId()).orElseThrow(()->new IllegalArgumentException("Ticket not found"));
        if(t.getReserved()<po.getQuantity()) throw new IllegalStateException("Ticket reservation is inconsistent; contact support before retrying");
        t.setReserved(t.getReserved()-po.getQuantity()); t.setSold(t.getSold()+po.getQuantity()); tickets.save(t);
        TicketPurchase purchase=new TicketPurchase(); purchase.setTicket(t); purchase.setAttendee(po.getAttendee()); purchase.setQuantity(po.getQuantity()); purchase.setTotalAmount(BigDecimal.valueOf(po.getAmountPaise(),2)); purchase.setPaymentMethod(Objects.toString(payment.get("method"),"RAZORPAY").toUpperCase(Locale.ROOT)); purchase.setReference("TKT-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT)); purchases.save(purchase);
        po.setStatus("PAID"); po.setRazorpayPaymentId(paymentId); po.setTicketReference(purchase.getReference()); po.setCompletedAt(LocalDateTime.now()); orders.save(po);
        if(!registrations.existsByEventAndAttendee(t.getEvent(),po.getAttendee())){ Registration r=new Registration(); r.setEvent(t.getEvent()); r.setAttendee(po.getAttendee()); registrations.save(r); }
        activity.log(po.getAttendee().getName()+" paid for "+po.getQuantity()+" x "+t.getName()+" via Razorpay"); return purchase;
    }
    private long number(Object n){ if(n instanceof Number) return ((Number)n).longValue(); return Long.parseLong(Objects.toString(n,"-1")); }
    // Pending orders reserve inventory for 15 minutes. Razorpay order expiry is also set to 15 minutes.
    @Scheduled(fixedDelayString="${payments.expiry-check-ms:60000}")
    @Transactional
    public void expirePendingOrders(){
        LocalDateTime cutoff=LocalDateTime.now().minusMinutes(15);
        List<PaymentOrder> expiring=new ArrayList<>();
        expiring.addAll(orders.findByStatusAndCreatedAtBefore("PENDING",cutoff));
        expiring.addAll(orders.findByStatusAndCreatedAtBefore("UPI_AWAITING_PAYMENT",cutoff));
        for(PaymentOrder po:expiring){
            Ticket t=tickets.findByIdForUpdate(po.getTicket().getId()).orElse(null);
            if(t!=null){t.setReserved(Math.max(0,t.getReserved()-po.getQuantity())); tickets.save(t);}
            po.setStatus("EXPIRED"); orders.save(po);
        }
    }
}
