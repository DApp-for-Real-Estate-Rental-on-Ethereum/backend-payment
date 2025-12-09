package ma.fstt.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ma.fstt.paymentservice.domain.entity.enums.TransactionStatusEnum;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@ToString
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = true)
    private Long bookingId; // Optional: for linking to booking

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tx_hash", nullable = true, columnDefinition = "TEXT")
    private String txHash;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatusEnum status = TransactionStatusEnum.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }
}


