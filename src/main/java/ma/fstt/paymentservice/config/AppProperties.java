package ma.fstt.paymentservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Security security = new Security();

    @Data
    public static class Security {
        private Boolean enabled = false;
    }
}

