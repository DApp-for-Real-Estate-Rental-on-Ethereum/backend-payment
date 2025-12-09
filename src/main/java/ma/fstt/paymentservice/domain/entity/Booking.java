package ma.fstt.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@ToString
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "property_id", nullable = true)
    private String propertyId; // Changed to String to support UUID from booking-service

    @Column(name = "check_in_date", nullable = false, columnDefinition = "DATE")
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false, columnDefinition = "DATE")
    private LocalDate checkOutDate;

    @Column(name = "on_chain_tx_hash", nullable = true, columnDefinition = "TEXT")
    private String onChainTxHash;

    // Changed from enum to String to match booking-service
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "total_price", nullable = true)
    private Double totalPrice;

    // Changed from ZonedDateTime to Instant to match booking-service
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = true, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;

    @Column(name = "long_stay_discount_percent", nullable = true)
    private Integer longStayDiscountPercent;

    @Column(name = "requested_negotiation_percent", nullable = true)
    private Integer requestedNegotiationPercent;

    @Column(name = "negotiation_expires_at", nullable = true, columnDefinition = "TIMESTAMP")
    private Instant negotiationExpiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

