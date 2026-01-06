package ma.fstt.paymentservice.api.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PaymentStatsDTO {
    private Long id; // userId
    private Long totalTransactions;
    private Long successfulTransactions;
    private Long failedTransactions;
    private Double avgTransactionAmount;
}
