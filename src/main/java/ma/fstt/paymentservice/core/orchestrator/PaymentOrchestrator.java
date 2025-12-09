package ma.fstt.paymentservice.core.orchestrator;

import lombok.RequiredArgsConstructor;
import ma.fstt.paymentservice.api.dto.PaymentIntentRequest;
import ma.fstt.paymentservice.core.blockchain.BookingPaymentContractService;
import ma.fstt.paymentservice.core.service.PropertyDatabaseService;
import ma.fstt.paymentservice.domain.entity.Booking;
import ma.fstt.paymentservice.domain.entity.TransactionRecord;
import ma.fstt.paymentservice.domain.repository.BookingRepository;
import ma.fstt.paymentservice.domain.repository.TransactionRepository;
import ma.fstt.paymentservice.exception.BusinessException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.fstt.paymentservice.domain.entity.UserAccount;
import ma.fstt.paymentservice.domain.entity.enums.TransactionStatusEnum;
import ma.fstt.paymentservice.domain.repository.UserAccountRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentOrchestrator {

    private final TransactionRepository transactionRepository;
    private final BookingRepository bookingRepository;
    private final UserAccountRepository userAccountRepository;
    private final BookingPaymentContractService contractService;
    private final PropertyDatabaseService propertyDatabaseService;

    @Value("${app.web3.contract-address:}")
    private String contractAddress;

    private static final Long CHAIN_ID = 31337L;

    @Transactional
    public PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request) {
        try {
            if (request.getBookingId() == null) {
                throw new BusinessException("INVALID_REQUEST", "bookingId is required");
            }

            Long bookingId = request.getBookingId();
            MDC.put("bookingId", String.valueOf(bookingId));

            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new BusinessException("BOOKING_NOT_FOUND", "Booking not found: " + bookingId));

            if (booking.getPropertyId() == null) {
                throw new BusinessException("PROPERTY_NOT_FOUND", "Booking has no property assigned");
            }

            String propertyIdStr = booking.getPropertyId();
            Map<String, Object> propertyResponse = propertyDatabaseService.getPropertyById(propertyIdStr);
            
            if (propertyResponse == null) {
                throw new BusinessException("PROPERTY_NOT_FOUND", "Property not found in database: " + propertyIdStr);
            }

            String ownerUserIdStr = propertyDatabaseService.extractOwnerUserId(propertyResponse);
            if (ownerUserIdStr == null || ownerUserIdStr.trim().isEmpty()) {
                throw new BusinessException("OWNER_NOT_FOUND", "Property does not have an owner userId");
            }

            Long ownerId;
            try {
                ownerId = Long.parseLong(ownerUserIdStr);
            } catch (NumberFormatException e) {
                throw new BusinessException("INVALID_OWNER_ID", 
                    "Property owner userId '" + ownerUserIdStr + "' cannot be converted to Long. " +
                    "userId in property-service must match id in payment-service users table.");
            }

            UserAccount owner = userAccountRepository.findById(ownerId)
                    .orElseThrow(() -> new BusinessException("OWNER_NOT_FOUND", 
                        "Property owner not found in payment-service users table with id: " + ownerId));

            if (owner.getWalletAddress() == null || owner.getWalletAddress().trim().isEmpty()) {
                throw new BusinessException("WALLET_ADDRESS_MISSING", "Property owner does not have a wallet address configured");
            }

            if (booking.getTotalPrice() == null) {
                throw new BusinessException("BOOKING_PRICE_MISSING", "Booking total price is not set in database. Booking ID: " + bookingId);
            }

            BigDecimal total = BigDecimal.valueOf(booking.getTotalPrice());

            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("INVALID_BOOKING_PRICE", "Booking total price must be greater than zero. Current value: " + total);
            }

            UserAccount guest = userAccountRepository.findById(booking.getUserId())
                    .orElseThrow(() -> new BusinessException("GUEST_NOT_FOUND", "Guest not found: " + booking.getUserId()));

            if (guest.getWalletAddress() == null || guest.getWalletAddress().trim().isEmpty()) {
                throw new BusinessException("WALLET_ADDRESS_MISSING", "Guest does not have a wallet address configured");
            }

            Double depositAmount = getDepositAmountFromProperty(propertyIdStr);
            if (depositAmount == null) {
                depositAmount = 0.0;
            }

            BigDecimal deposit = BigDecimal.valueOf(depositAmount);
            BigDecimal rentAmount = total;

            BigInteger rentAmountWei = rentAmount.multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L)).toBigInteger();
            BigInteger depositAmountWei = deposit.multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L)).toBigInteger();

            String contractAddress = getContractAddress();
            String functionData = null;
            if (contractAddress != null && !contractAddress.isEmpty()) {
                try {
                    functionData = contractService.getCreateBookingPaymentData(
                            bookingId,
                            owner.getWalletAddress(),
                            guest.getWalletAddress(),
                            rentAmountWei,
                            depositAmountWei
                    );
                } catch (Exception e) {
                }
            }

            BigDecimal totalWithDeposit = total.add(deposit);
            
            return createAndPersistTx(
                    bookingId,
                    booking.getUserId(), 
                    totalWithDeposit,
                    contractAddress != null && !contractAddress.isEmpty() ? contractAddress : owner.getWalletAddress(),
                    functionData
            );
        } finally {
            MDC.clear();
        }
    }

    private PaymentIntentResponse createAndPersistTx(Long bookingId, Long userId, BigDecimal total, String toAddress, String functionData) {
        BigInteger totalAmountWei = total.multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L)).toBigInteger();
        UUID referenceId = UUID.randomUUID();

        TransactionRecord tx = new TransactionRecord();
        tx.setBookingId(bookingId);
        tx.setUserId(userId);
        tx.setTxHash("pending-" + referenceId);
        tx.setAmount(total);
        tx.setStatus(TransactionStatusEnum.PENDING);
        transactionRepository.save(tx);

        return PaymentIntentResponse.builder()
                .referenceId(referenceId)
                .to(toAddress)
                .value(totalAmountWei.toString())
                .data(functionData)
                .chainId(CHAIN_ID)
                .totalAmountWei(totalAmountWei.toString())
                .build();
    }

    private Double getDepositAmountFromProperty(String propertyId) {
        try {
            Map<String, Object> propertyResponse = propertyDatabaseService.getPropertyById(propertyId);
            return propertyDatabaseService.extractDepositAmount(propertyResponse);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String getContractAddress() {
        return contractAddress;
    }

    @Transactional
    public void completeBooking(Long bookingId) {
        try {
            MDC.put("bookingId", String.valueOf(bookingId));

            if (contractAddress == null || contractAddress.isEmpty()) {
                throw new BusinessException("CONTRACT_NOT_CONFIGURED", "Smart contract address not configured");
            }

            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> {
                        return new BusinessException("BOOKING_NOT_FOUND", "Booking not found: " + bookingId);
                    });

            if (booking.getPropertyId() != null) {
                try {
                    Map<String, Object> propertyResponse = propertyDatabaseService.getPropertyById(booking.getPropertyId());
                    String ownerWalletAddress = propertyDatabaseService.extractOwnerUserId(propertyResponse);
                    
                    try {
                        Long ownerId = Long.parseLong(ownerWalletAddress);
                        UserAccount owner = userAccountRepository.findById(ownerId).orElse(null);
                    } catch (NumberFormatException e) {
                    }
                } catch (Exception e) {
                }
            }

            try {
                String fromAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
                boolean exists = contractService.bookingExists(bookingId, fromAddress);
                if (!exists) {
                    throw new BusinessException("BOOKING_NOT_ON_BLOCKCHAIN", 
                        "Booking " + bookingId + " does not exist on blockchain. Payment must be made first.");
                }
            } catch (Exception e) {
            }

            String txHash = contractService.completeBooking(bookingId);

            TransactionRecord transaction = transactionRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
                    .orElse(null);
            
            if (transaction != null) {
                transaction.setTxHash(txHash);
                transaction.setStatus(TransactionStatusEnum.SUCCESS);
                transactionRepository.save(transaction);
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("BLOCKCHAIN_ERROR", "Failed to complete booking on blockchain: " + e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    @Transactional
    public void cancelOverlappingBookings(Long confirmedBookingId) {
        try {
            Booking confirmedBooking = bookingRepository.findById(confirmedBookingId)
                    .orElse(null);
            
            if (confirmedBooking == null) {
                return;
            }
            
            if (!"CONFIRMED".equals(confirmedBooking.getStatus())) {
                return;
            }
            
            String propertyId = confirmedBooking.getPropertyId();
            LocalDate checkIn = confirmedBooking.getCheckInDate();
            LocalDate checkOut = confirmedBooking.getCheckOutDate();
            
            if (propertyId == null || checkIn == null || checkOut == null) {
                return;
            }
            
            List<Booking> allPropertyBookings = bookingRepository.findByPropertyId(propertyId);
            
            List<Booking> overlappingBookings = bookingRepository.findOverlappingBookings(
                    propertyId, 
                    confirmedBookingId, 
                    checkIn, 
                    checkOut
            );
            
            overlappingBookings.removeIf(b -> {
                boolean isConfirmed = b.getId().equals(confirmedBookingId);
                return isConfirmed;
            });
            
            if (overlappingBookings.isEmpty()) {
                return;
            }
            
            int deletedCount = 0;
            int skippedCount = 0;
            for (Booking overlappingBooking : overlappingBookings) {
                if (overlappingBooking.getId().equals(confirmedBookingId)) {
                    continue;
                }
                
                String previousStatus = overlappingBooking.getStatus();
                
                if ("COMPLETED".equals(previousStatus)) {
                    skippedCount++;
                    continue;
                }
                
                try {
                    Long bookingIdToDelete = overlappingBooking.getId();
                    bookingRepository.delete(overlappingBooking);
                    deletedCount++;
                } catch (Exception e) {
                }
            }
            
        } catch (Exception e) {
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class PaymentIntentResponse {
        private UUID referenceId;
        private String to;
        private String value;
        private String data;
        private Long chainId;
        private String totalAmountWei;
    }
}
