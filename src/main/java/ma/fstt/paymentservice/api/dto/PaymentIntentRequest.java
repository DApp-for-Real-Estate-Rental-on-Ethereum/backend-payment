package ma.fstt.paymentservice.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentIntentRequest {
    // Only bookingId is required - payment service only supports blockchain payments via bookings
    @NotNull(message = "bookingId is required")
    private Long bookingId;
}

