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
public class BookingStatusResponse {
    private String bookingId;
    private String state;
    private BigDecimal priceWei;
    private BigDecimal depositWei;
    private String lastEventType;
    private Long lastEventBlock;
}

