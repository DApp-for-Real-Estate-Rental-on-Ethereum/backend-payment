package ma.fstt.paymentservice.domain.repository;

import ma.fstt.paymentservice.domain.entity.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {
    Optional<TransactionRecord> findByTxHash(String txHash);

    Optional<TransactionRecord> findFirstByBookingIdOrderByCreatedAtDesc(Long bookingId);

    Long countByUserId(Long userId);

    Long countByUserIdAndStatus(Long userId, ma.fstt.paymentservice.domain.entity.enums.TransactionStatusEnum status);

    @org.springframework.data.jpa.repository.Query("SELECT AVG(t.amount) FROM TransactionRecord t WHERE t.userId = :userId")
    Double getAvgTransactionAmountByUserId(Long userId);
}
