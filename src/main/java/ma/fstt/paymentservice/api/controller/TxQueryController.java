package ma.fstt.paymentservice.api.controller;

import lombok.RequiredArgsConstructor;
import ma.fstt.paymentservice.api.dto.TxStatusResponse;
import ma.fstt.paymentservice.domain.repository.TransactionRepository;
import ma.fstt.paymentservice.exception.BusinessException;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class TxQueryController {

    private final TransactionRepository transactionRepository;

    @GetMapping("/tx/{hash}")
    public ResponseEntity<TxStatusResponse> getTransactionStatus(@PathVariable String hash) {
        try {
            MDC.put("txHash", hash);

            return transactionRepository.findByTxHash(hash)
                    .map(tx -> {
                        String statusString = tx.getStatus() != null ? tx.getStatus().name() : "UNKNOWN";

                        TxStatusResponse response = TxStatusResponse.builder()
                                .txHash(tx.getTxHash())
                                .status(statusString)
                                .blockNumber(null)
                                .bookingId(tx.getBookingId() != null ? tx.getBookingId().toString() : null)
                                .build();
                        return ResponseEntity.ok(response);
                    })
                    .orElseThrow(() -> new BusinessException("TX_NOT_FOUND", "Transaction not found: " + hash));
        } finally {
            MDC.remove("txHash");
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<ma.fstt.paymentservice.api.dto.PaymentStatsDTO> getPaymentStats(
            @RequestParam Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String requesterId,
            @RequestHeader(value = "X-User-Roles", required = false) String requesterRoles) {

        boolean isAdmin = requesterRoles != null && requesterRoles.contains("ADMIN");
        boolean isSelf = requesterId != null && requesterId.equals(userId.toString());

        if (!isAdmin && !isSelf) {
            return ResponseEntity.status(403).build();
        }

        Long total = transactionRepository.countByUserId(userId);
        Long success = transactionRepository.countByUserIdAndStatus(userId,
                ma.fstt.paymentservice.domain.entity.enums.TransactionStatusEnum.SUCCESS);
        Long failed = transactionRepository.countByUserIdAndStatus(userId,
                ma.fstt.paymentservice.domain.entity.enums.TransactionStatusEnum.FAILED);
        Double avg = transactionRepository.getAvgTransactionAmountByUserId(userId);

        return ResponseEntity.ok(ma.fstt.paymentservice.api.dto.PaymentStatsDTO.builder()
                .id(userId)
                .totalTransactions(total != null ? total : 0L)
                .successfulTransactions(success != null ? success : 0L)
                .failedTransactions(failed != null ? failed : 0L)
                .avgTransactionAmount(avg != null ? avg : 0.0)
                .build());
    }

}
