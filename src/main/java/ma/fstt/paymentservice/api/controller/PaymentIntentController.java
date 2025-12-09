package ma.fstt.paymentservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.fstt.paymentservice.api.dto.BookingDetailsResponse;
import ma.fstt.paymentservice.api.dto.PaymentIntentRequest;
import ma.fstt.paymentservice.api.dto.PaymentIntentResponse;
import ma.fstt.paymentservice.api.dto.PropertyInfoResponse;
import ma.fstt.paymentservice.api.dto.WalletAddressRequest;
import ma.fstt.paymentservice.core.orchestrator.PaymentOrchestrator;
import ma.fstt.paymentservice.core.service.PropertyDatabaseService;
import ma.fstt.paymentservice.domain.entity.Booking;
import ma.fstt.paymentservice.domain.entity.UserAccount;
import ma.fstt.paymentservice.domain.entity.TransactionRecord;
import ma.fstt.paymentservice.domain.repository.BookingRepository;
import ma.fstt.paymentservice.domain.repository.UserAccountRepository;
import ma.fstt.paymentservice.domain.repository.TransactionRepository;
import ma.fstt.paymentservice.core.messaging.BookingCreatedConsumer;
import ma.fstt.paymentservice.exception.BusinessException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentIntentController {

    private final PaymentOrchestrator paymentOrchestrator;
    private final UserAccountRepository userAccountRepository;
    private final TransactionRepository transactionRepository;
    private final BookingRepository bookingRepository;
    private final BookingCreatedConsumer bookingCreatedConsumer;
    private final PropertyDatabaseService propertyDatabaseService;
    private final ma.fstt.paymentservice.core.blockchain.BookingPaymentContractService contractService;

    @Value("${app.booking-service.url:http://localhost:8083}")
    private String bookingServiceUrl;

    @PostMapping("/intent")
    public ResponseEntity<PaymentIntentResponse> createPaymentIntent(@Valid @RequestBody PaymentIntentRequest request) {
        try {
            MDC.put("bookingId", String.valueOf(request.getBookingId()));

            PaymentOrchestrator.PaymentIntentResponse orchestratorResponse =
                    paymentOrchestrator.createPaymentIntent(request);

            PaymentIntentResponse response = PaymentIntentResponse.builder()
                    .referenceId(orchestratorResponse.getReferenceId())
                    .to(orchestratorResponse.getTo())
                    .value(orchestratorResponse.getValue())
                    .data(orchestratorResponse.getData())
                    .chainId(orchestratorResponse.getChainId())
                    .totalAmountWei(orchestratorResponse.getTotalAmountWei())
                    .build();

            return ResponseEntity.ok(response);
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<BookingDetailsResponse> getBookingDetails(@PathVariable Long bookingId) {
        try {
            MDC.put("bookingId", String.valueOf(bookingId));

            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> {
                        return new BusinessException("BOOKING_NOT_FOUND", 
                            "Booking with ID " + bookingId + " not found. Please make sure the booking exists in the database.");
                    });

            if (booking.getPropertyId() == null) {
                throw new BusinessException("PROPERTY_NOT_FOUND", "Booking has no property assigned");
            }

            String propertyIdStr = booking.getPropertyId();
            
            Map<String, Object> propertyResponse;
            try {
                propertyResponse = propertyDatabaseService.getPropertyById(propertyIdStr);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                throw new BusinessException("DATABASE_ERROR", 
                    String.format("Error fetching property '%s' from database: %s", 
                        propertyIdStr, errorMessage));
            }
            
            if (propertyResponse == null) {
                throw new BusinessException("PROPERTY_NOT_FOUND", 
                    "Property with ID " + propertyIdStr + " not found in database (null response)");
            }
            
            String ownerUserIdStr = propertyDatabaseService.extractOwnerUserId(propertyResponse);
            String propertyTitle = propertyResponse.containsKey("title") ? 
                propertyResponse.get("title").toString() : propertyIdStr;
            Double propertyPrice = propertyDatabaseService.extractDailyPrice(propertyResponse);
            
            Long ownerId = null;
            if (ownerUserIdStr != null && !ownerUserIdStr.trim().isEmpty()) {
                try {
                    ownerId = Long.parseLong(ownerUserIdStr);
                } catch (NumberFormatException e) {
                }
            }

            UserAccount ownerAccount = null;
            if (ownerId != null) {
                ownerAccount = userAccountRepository.findById(ownerId).orElse(null);
            }

            UserAccount userAccount = userAccountRepository.findById(booking.getUserId()).orElse(null);

            String statusString = booking.getStatus() != null ? booking.getStatus() : "PENDING";

            String ownerWalletAddress = ownerAccount != null ? ownerAccount.getWalletAddress() : null;
            String userWalletAddress = userAccount != null ? userAccount.getWalletAddress() : null;
            
            if (ownerWalletAddress != null) {
                ownerWalletAddress = ownerWalletAddress.trim();
                if (ownerWalletAddress.isEmpty()) {
                    ownerWalletAddress = null;
                }
            }
            
            if (userWalletAddress != null) {
                userWalletAddress = userWalletAddress.trim();
                if (userWalletAddress.isEmpty()) {
                    userWalletAddress = null;
                }
            }
            
            if (ownerWalletAddress == null || ownerWalletAddress.trim().isEmpty() || ownerWalletAddress.equalsIgnoreCase("null")) {
                String errorMessage = String.format(
                    "Property owner wallet address is missing. " +
                    "Property ID: %s, Owner userId from property-service: '%s', Owner ID (Long): %s. " +
                    "Please ensure: 1) Property owner has set their wallet address in payment-service, " +
                    "2) userId in property-service matches id in payment-service users table.",
                    propertyIdStr, ownerUserIdStr, ownerId
                );
                throw new BusinessException("OWNER_WALLET_ADDRESS_MISSING", errorMessage);
            }

            BookingDetailsResponse response = BookingDetailsResponse.builder()
                    .bookingId(booking.getId())
                    .status(statusString)
                    .totalPrice(booking.getTotalPrice())
                    .checkInDate(booking.getCheckInDate())
                    .checkOutDate(booking.getCheckOutDate())
                    .propertyId(propertyIdStr)
                    .propertyTitle(propertyTitle)
                    .propertyPrice(propertyPrice)
                    .ownerWalletAddress(ownerWalletAddress)
                    .userId(booking.getUserId())
                    .currentUserId(booking.getUserId())
                    .userFirstName(userAccount != null ? userAccount.getFirstName() : null)
                    .userLastName(userAccount != null ? userAccount.getLastName() : null)
                    .userEmail(userAccount != null ? userAccount.getEmail() : null)
                    .userWalletAddress(userWalletAddress)
                    .build();

            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("INTERNAL_ERROR", 
                "An error occurred while fetching booking details: " + e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/booking-id")
    public ResponseEntity<Map<String, Object>> getBookingId() {
        try {
            Long lastBookingId = bookingCreatedConsumer.getLastReceivedBookingId();
            
            if (lastBookingId != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("bookingId", lastBookingId);
                response.put("status", "success");
                return ResponseEntity.ok(response);
            }
            
            Long bookingId = bookingCreatedConsumer.pollBookingId(5, TimeUnit.SECONDS);
            
            if (bookingId != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("bookingId", bookingId);
                response.put("status", "success");
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "not_found");
                response.put("message", "No bookingId received from RabbitMQ yet");
                return ResponseEntity.status(204).body(response);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/properties/{id}")
    public ResponseEntity<PropertyInfoResponse> getPropertyInfo(@PathVariable String id) {
        try {
            MDC.put("propertyId", id);

            Map<String, Object> propertyResponse = propertyDatabaseService.getPropertyById(id);

            if (propertyResponse == null || !propertyResponse.containsKey("id")) {
                throw new BusinessException("PROPERTY_NOT_FOUND", "Property not found in database: " + id);
            }

            Long ownerId = null;
            String ownerUserIdStr = propertyDatabaseService.extractOwnerUserId(propertyResponse);
            if (ownerUserIdStr != null && !ownerUserIdStr.trim().isEmpty()) {
                try {
                    ownerId = Long.parseLong(ownerUserIdStr);
                } catch (NumberFormatException e) {
                }
            }

            Integer maxNegotiationPercent = null;
            boolean isNegotiable = false;
            if (propertyResponse.containsKey("maxNegotiationPercent")) {
                Object negObj = propertyResponse.get("maxNegotiationPercent");
                if (negObj instanceof Number) {
                    double negPercent = ((Number) negObj).doubleValue();
                    maxNegotiationPercent = (int) negPercent;
                    isNegotiable = negPercent > 0;
                }
            } else if (propertyResponse.containsKey("isNegotiable")) {
                isNegotiable = Boolean.TRUE.equals(propertyResponse.get("isNegotiable"));
            }

            boolean discountEnabled = propertyResponse.containsKey("discountEnabled") && 
                Boolean.TRUE.equals(propertyResponse.get("discountEnabled"));

            Double dailyPrice = propertyDatabaseService.extractDailyPrice(propertyResponse);

            PropertyInfoResponse response = PropertyInfoResponse.builder()
                    .id(id)
                    .ownerId(ownerId)
                    .pricePerNight(dailyPrice > 0 ? BigDecimal.valueOf(dailyPrice) : null)
                    .maxNegotiationPercent(maxNegotiationPercent)
                    .discountEnabled(discountEnabled)
                    .isNegotiable(isNegotiable)
                    .build();

            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("DATABASE_ERROR", "Error fetching property from database: " + e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    @PutMapping("/wallet-address")
    public ResponseEntity<Void> updateWalletAddress(@Valid @RequestBody WalletAddressRequest request) {
        try {
            MDC.put("currentUserId", String.valueOf(request.getUserId()));

            UserAccount user = userAccountRepository.findById(request.getUserId())
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + request.getUserId()));

            user.setWalletAddress(request.getWalletAddress());
            userAccountRepository.save(user);

            return ResponseEntity.ok().build();
        } finally {
            MDC.clear();
        }
    }

    @PutMapping("/booking/{bookingId}/tx-hash")
    public ResponseEntity<Void> updateTransactionHash(@PathVariable Long bookingId, @RequestBody java.util.Map<String, String> request) {
        try {
            MDC.put("bookingId", String.valueOf(bookingId));
            String txHash = request.get("txHash");

            TransactionRecord transaction = transactionRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
                    .orElseThrow(() -> new BusinessException("TRANSACTION_NOT_FOUND", "Transaction not found for booking: " + bookingId));

            transaction.setTxHash(txHash);
            transaction.setStatus(ma.fstt.paymentservice.domain.entity.enums.TransactionStatusEnum.SUCCESS);
            transactionRepository.save(transaction);

            try {
                Booking booking = bookingRepository.findById(bookingId)
                        .orElse(null);
                
                if (booking != null && (
                    "PENDING_PAYMENT".equals(booking.getStatus()) ||
                    "PENDING_NEGOTIATION".equals(booking.getStatus()) ||
                    "PENDING".equals(booking.getStatus())
                )) {
                    String previousStatus = booking.getStatus();
                    booking.setStatus("CONFIRMED");
                    bookingRepository.save(booking);
                    
                    try {
                        paymentOrchestrator.cancelOverlappingBookings(bookingId);
                    } catch (Exception e) {
                    }
                    
                    updateBookingStatusInBookingService(bookingId, "CONFIRMED");
                } else if (booking != null) {
                    if (!"CONFIRMED".equals(booking.getStatus())) {
                        updateBookingStatusInBookingService(bookingId, "CONFIRMED");
                    }
                } else {
                    updateBookingStatusInBookingService(bookingId, "CONFIRMED");
                }
            } catch (Exception e) {
                try {
                    updateBookingStatusInBookingService(bookingId, "CONFIRMED");
                } catch (Exception ex) {
                }
            }

            return ResponseEntity.ok().build();
        } finally {
            MDC.clear();
        }
    }

    private void updateBookingStatusInBookingService(Long bookingId, String status) {
        try {
            if (bookingServiceUrl == null || bookingServiceUrl.isEmpty()) {
                return;
            }

            RestTemplate restTemplate = new RestTemplate();
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(5000);
            restTemplate.setRequestFactory(factory);
            
            String url = bookingServiceUrl + "/api/bookings/" + bookingId + "/status";
            
            Map<String, String> statusUpdate = new HashMap<>();
            statusUpdate.put("status", status);
            
            restTemplate.put(url, statusUpdate);
        } catch (Exception e) {
        }
    }

    @PostMapping("/reclamation/refund")
    public ResponseEntity<Map<String, Object>> processReclamationRefund(@RequestBody ReclamationRefundRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            MDC.put("bookingId", String.valueOf(request.getBookingId()));

            BigInteger refundAmountWei = new BigInteger(request.getRefundAmountWei());
            BigInteger penaltyAmountWei = new BigInteger(request.getPenaltyAmountWei());

            String txHash = contractService.processReclamationRefund(
                    request.getBookingId(),
                    request.getRecipientAddress(),
                    refundAmountWei,
                    penaltyAmountWei,
                    request.isRefundFromRent()
            );

            response.put("status", "success");
            response.put("message", "Reclamation refund processed successfully");
            response.put("txHash", txHash);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to process refund: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        } finally {
            MDC.clear();
        }
    }

    @PostMapping("/reclamation/set-active")
    public ResponseEntity<Map<String, Object>> setActiveReclamation(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long bookingId = Long.valueOf(request.get("bookingId").toString());
            Boolean active = Boolean.valueOf(request.get("active").toString());
            
            String txHash = contractService.setActiveReclamation(bookingId, active);
            
            response.put("status", "success");
            response.put("message", "Active reclamation status updated");
            response.put("txHash", txHash);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to set active reclamation: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/reclamation/partial-refund")
    public ResponseEntity<Map<String, Object>> processPartialRefund(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long bookingId = Long.valueOf(request.get("bookingId").toString());
            String recipientAddress = request.get("recipientAddress").toString();
            BigInteger refundAmountWei = new BigInteger(request.get("refundAmountWei").toString());
            Boolean refundFromRent = Boolean.valueOf(request.get("refundFromRent").toString());

            String txHash = contractService.processPartialRefund(
                    bookingId,
                    recipientAddress,
                    refundAmountWei,
                    refundFromRent
            );

            response.put("status", "success");
            response.put("message", "Partial refund processed successfully");
            response.put("txHash", txHash);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to process partial refund: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/booking/{bookingId}/complete")
    public ResponseEntity<Map<String, String>> completeBooking(@PathVariable Long bookingId) {
        try {
            MDC.put("bookingId", String.valueOf(bookingId));

            paymentOrchestrator.completeBooking(bookingId);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Booking completed successfully on blockchain");
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("code", e.getCode());
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (IllegalStateException e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (RuntimeException e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            if (e.getCause() != null) {
                response.put("cause", e.getCause().getMessage());
            }
            return ResponseEntity.status(500).body(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage() != null ? e.getMessage() : "Unknown error occurred");
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                response.put("cause", e.getCause().getMessage());
            }
            return ResponseEntity.status(500).body(response);
        } finally {
            MDC.clear();
        }
    }

    @lombok.Data
    private static class ReclamationRefundRequest {
        private Long bookingId;
        private String recipientAddress;
        private String refundAmountWei;
        private String penaltyAmountWei;
        private boolean refundFromRent;
    }
}
