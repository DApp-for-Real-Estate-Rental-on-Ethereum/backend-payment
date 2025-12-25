package ma.fstt.paymentservice.config;

import org.springframework.context.annotation.Configuration;

// CORS is handled by the API Gateway - no need to configure here
// Removing CORS config to prevent duplicate headers

@Configuration
public class CorsConfig {
    // Empty - CORS handled by Gateway
}
