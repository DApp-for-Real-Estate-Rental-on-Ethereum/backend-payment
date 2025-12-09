package ma.fstt.paymentservice.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "payments-svc");
        response.put("status", "UP");
        response.put("version", "1.0.0");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("health", "/health");
        endpoints.put("booking", "/api/payments/booking/{bookingId}");
        endpoints.put("paymentIntent", "/api/payments/intent");
        endpoints.put("transaction", "/api/payments/tx/{hash}");
        endpoints.put("walletAddress", "/api/payments/wallet-address");
        
        response.put("endpoints", endpoints);
        return ResponseEntity.ok(response);
    }
}

