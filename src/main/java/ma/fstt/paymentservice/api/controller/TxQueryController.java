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

    
}

