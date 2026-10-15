package com.eventmgmt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.util.Map;

@Service
public class RazorpayGateway {
    private final RestClient client;
    private final String keyId;
    private final String keySecret;
    public RazorpayGateway(RestClient.Builder builder,
            @Value("${razorpay.key-id:}") String keyId,
            @Value("${razorpay.key-secret:}") String keySecret) {
        this.client = builder.baseUrl("https://api.razorpay.com/v1").build();
        this.keyId = keyId; this.keySecret = keySecret;
    }
    public String keyId() { return keyId; }
    public void requireConfigured() { if (keyId.isBlank() || keySecret.isBlank()) throw new IllegalStateException("Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET."); }
    @SuppressWarnings("unchecked")
    public Map<String,Object> createOrder(Map<String,Object> body) { requireConfigured(); return client.post().uri("/orders").headers(h -> h.setBasicAuth(keyId,keySecret)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class); }
    @SuppressWarnings("unchecked")
    public Map<String,Object> fetchPayment(String paymentId) { requireConfigured(); return client.get().uri("/payments/{id}", paymentId).headers(h -> h.setBasicAuth(keyId,keySecret)).retrieve().body(Map.class); }
}
