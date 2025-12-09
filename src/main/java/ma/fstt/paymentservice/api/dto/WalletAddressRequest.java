package ma.fstt.paymentservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WalletAddressRequest {
    @NotNull(message = "User ID is required")
    private Long userId;
    
    @NotBlank(message = "Wallet address is required")
    private String walletAddress;
}

