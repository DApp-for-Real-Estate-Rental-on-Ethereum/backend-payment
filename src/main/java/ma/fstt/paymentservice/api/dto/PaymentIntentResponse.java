package ma.fstt.paymentservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentResponse {
    private UUID referenceId;
    private String to;
    private String value;
    private String data;
    private Long chainId;
    private String totalAmountWei;
}

