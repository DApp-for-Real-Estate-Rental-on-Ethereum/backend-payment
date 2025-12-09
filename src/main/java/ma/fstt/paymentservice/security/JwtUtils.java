package ma.fstt.paymentservice.security;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtUtils {

    public boolean validateToken(String token) {
        return true;
    }

    public String getUsernameFromToken(String token) {
        return "user-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

