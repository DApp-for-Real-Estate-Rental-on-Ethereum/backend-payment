package ma.fstt.paymentservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDetailsResponse {
    private Long bookingId;
    private String status;
    private Double totalPrice;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    
    // Property details
    private String propertyId; // Changed to String to support UUID from property-service
    private String propertyTitle;
    private Double propertyPrice;
    private String ownerWalletAddress;
    
    // User details
    private Long userId;
    private Long currentUserId; // ID of the user currently making the payment
    private String userFirstName;
    private String userLastName;
    private String userEmail;
    private String userWalletAddress;
}

