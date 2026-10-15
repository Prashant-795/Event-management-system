package com.eventmgmt.controller;

import com.eventmgmt.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/webhooks/razorpay")
public class RazorpayWebhookController {
    private final PaymentService payments; private final ObjectMapper mapper;
    @Value("${razorpay.webhook-secret:}") private String webhookSecret;
    public RazorpayWebhookController(PaymentService payments, ObjectMapper mapper){this.payments=payments;this.mapper=mapper;}
    @PostMapping
    public ResponseEntity<String> receive(@RequestBody String body, @RequestHeader(value="X-Razorpay-Signature", required=false) String signature) {
        if(webhookSecret==null || webhookSecret.isBlank()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Webhook secret not configured");
        if(!valid(body,signature)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature");
        try {
            JsonNode root=mapper.readTree(body); String event=root.path("event").asText();
            if("payment.captured".equals(event)) {
                JsonNode payment=root.path("payload").path("payment").path("entity");
                String orderId=payment.path("order_id").asText(""); String paymentId=payment.path("id").asText("");
                if(!orderId.isBlank() && !paymentId.isBlank()) payments.completeFromVerifiedWebhook(orderId,paymentId);
            }
            return ResponseEntity.ok("received");
        } catch(Exception ex) { return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook could not be processed"); }
    }
    private boolean valid(String body,String signature) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); byte[] hash=mac.doFinal(body.getBytes(StandardCharsets.UTF_8)); StringBuilder hex=new StringBuilder(); for(byte b:hash) hex.append(String.format("%02x",b)); return MessageDigest.isEqual(hex.toString().getBytes(StandardCharsets.US_ASCII), (signature==null?"":signature).getBytes(StandardCharsets.US_ASCII)); } catch(Exception e){return false;}
    }
}
