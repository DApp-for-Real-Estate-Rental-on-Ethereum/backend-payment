package ma.fstt.paymentservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TxStatusResponse {
    private String txHash;
    private String status;
    private Long blockNumber;
    private String bookingId;
}

