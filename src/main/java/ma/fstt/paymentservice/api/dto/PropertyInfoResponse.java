package ma.fstt.paymentservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyInfoResponse {
    private String id; // Changed to String to match property-service (uses UUID)
    private Long ownerId;
    private BigDecimal pricePerNight;
    private Integer maxNegotiationPercent;
    private Boolean discountEnabled;
    private Boolean isNegotiable;
}

