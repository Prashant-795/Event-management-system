package com.eventmgmt.controller;

import com.eventmgmt.model.*;
import com.eventmgmt.service.PaymentService;
import com.eventmgmt.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import org.springframework.web.client.RestClientException;

@Controller
@RequestMapping("/attendee/payments")
public class PaymentController {
    private final PaymentService payments; private final UserService users;
    @Value("${razorpay.key-id:}") private String keyId;
    public PaymentController(PaymentService payments, UserService users){this.payments=payments;this.users=users;}
    @PostMapping("/create-order")
    public String createOrder(@RequestParam Long ticketId, @RequestParam int quantity, @RequestParam(required=false) Long eventId, Principal principal, Model model, RedirectAttributes ra){
        try { User attendee=users.findByEmail(principal.getName()); PaymentOrder order=payments.createOrder(ticketId,attendee,quantity);
            model.addAttribute("keyId",keyId); model.addAttribute("order",order); model.addAttribute("attendee",attendee); model.addAttribute("event",order.getTicket().getEvent());
            model.addAttribute("amountRupees",String.format(java.util.Locale.ROOT,"%.2f",order.getAmountPaise()/100.0)); return "payment-checkout";
        } catch (IllegalArgumentException | IllegalStateException ex){ra.addFlashAttribute("error",ex.getMessage()); return "redirect:/events/" + (eventId == null ? "" : eventId);}
        catch (RestClientException ex){ra.addFlashAttribute("error","Razorpay could not create the payment order. Check test credentials and Route access, then try again."); return "redirect:/events/" + (eventId == null ? "" : eventId);}
    }
    @PostMapping("/direct/create")
    public String createDirectUpi(@RequestParam Long ticketId, @RequestParam int quantity,
                                  @RequestParam(required=false) Long eventId, Principal principal,
                                  RedirectAttributes ra) {
        try {
            PaymentOrder order=payments.createDirectUpiOrder(ticketId, users.findByEmail(principal.getName()), quantity);
            return "redirect:/attendee/payments/direct/"+order.getId();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/events/" + (eventId == null ? "" : eventId);
        }
    }

    @GetMapping("/direct/{id}")
    public String directUpiCheckout(@PathVariable Long id, Principal principal, Model model, RedirectAttributes ra) {
        try {
            PaymentOrder order=payments.getDirectUpiOrder(id, users.findByEmail(principal.getName()));
            model.addAttribute("order", order);
            model.addAttribute("upiId", order.getTicket().getEvent().getOrganizer().getUpiId());
            model.addAttribute("organizerName", order.getTicket().getEvent().getOrganizer().getName());
            model.addAttribute("event", order.getTicket().getEvent());
            model.addAttribute("amountRupees", String.format(java.util.Locale.ROOT, "%.2f", order.getAmountPaise()/100.0));
            return "direct-upi-checkout";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/attendee/tickets";
        }
    }

    @PostMapping("/direct/{id}/submit")
    public String submitDirectUpi(@PathVariable Long id, @RequestParam String transactionId,
                                  Principal principal, RedirectAttributes ra) {
        try {
            payments.submitDirectUpiReference(id, users.findByEmail(principal.getName()), transactionId);
            ra.addFlashAttribute("success", "UPI reference submitted. Tickets will be issued only after the organizer verifies the payment in their bank/UPI account.");
            return "redirect:/attendee/tickets";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/attendee/payments/direct/"+id;
        }
    }

    @PostMapping("/verify")
    public String verify(@RequestParam("razorpay_order_id") String orderId,@RequestParam("razorpay_payment_id") String paymentId,@RequestParam("razorpay_signature") String signature,Principal principal,RedirectAttributes ra){
        try { TicketPurchase p=payments.verifyAndComplete(orderId,paymentId,signature,users.findByEmail(principal.getName())); ra.addFlashAttribute("success","Payment verified. Ticket reference: "+p.getReference()); return "redirect:/attendee/tickets"; }
        catch (IllegalArgumentException | IllegalStateException ex){ra.addFlashAttribute("error",ex.getMessage()); return "redirect:/attendee/tickets";}
        catch (RestClientException ex){ra.addFlashAttribute("error","Could not verify the payment with Razorpay. If money was deducted, do not retry immediately; contact support and check the Razorpay dashboard."); return "redirect:/attendee/tickets";}
    }
}
