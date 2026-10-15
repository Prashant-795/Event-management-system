package com.eventmgmt;

import com.eventmgmt.repository.*;
import com.eventmgmt.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PaymentSignatureTest {
    private PaymentService service;
    private static final String SECRET = "test_only_secret";

    @BeforeEach
    void setup() {
        service = new PaymentService(mock(RazorpayGateway.class), mock(TicketRepository.class),
                mock(PaymentOrderRepository.class), mock(TicketPurchaseRepository.class),
                mock(RegistrationRepository.class), mock(ActivityService.class));
        ReflectionTestUtils.setField(service, "keySecret", SECRET);
    }

    @Test
    void acceptsValidCheckoutSignature() throws Exception {
        String orderId = "order_test123";
        String paymentId = "pay_test123";
        assertTrue(service.verifySignature(orderId, paymentId, signature(orderId + "|" + paymentId)));
    }

    @Test
    void rejectsInvalidCheckoutSignature() {
        assertFalse(service.verifySignature("order_test123", "pay_test123", "not-a-valid-signature"));
    }

    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format("%02x", b));
        return result.toString();
    }
}
