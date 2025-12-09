package ma.fstt.paymentservice.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PropertyServiceClient {

    private final RestTemplate restTemplate;
    private final String propertyServiceBaseUrl;

    public PropertyServiceClient(@Value("${app.property-service.url:http://localhost:8082}") String propertyServiceBaseUrl) {
        this.propertyServiceBaseUrl = propertyServiceBaseUrl;
        this.restTemplate = createRestTemplate();
    }

    public String getPropertyServiceBaseUrl() {
        return propertyServiceBaseUrl;
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(5)); // 5 seconds connection timeout
        factory.setReadTimeout((int) TimeUnit.SECONDS.toMillis(10)); // 10 seconds read timeout
        
        RestTemplate template = new RestTemplate(factory);
        return template;
    }

    public Map<String, Object> getPropertyById(String propertyId) {
        if (propertyId == null || propertyId.trim().isEmpty()) {
            throw new IllegalArgumentException("Property ID cannot be null or empty");
        }

        String url = propertyServiceBaseUrl + "/api/v1/properties/" + propertyId;

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                return null;
            }
        } catch (HttpClientErrorException.NotFound e) {
            String errorMsg = String.format(
                "Property not found in property-service: %s. " +
                "URL: %s, Response: %s", propertyId, url, e.getResponseBodyAsString()
            );
            throw new RuntimeException("Property not found: " + propertyId, e);
        } catch (HttpClientErrorException e) {
            String errorMsg = String.format(
                "Client error when calling property-service for property %s. " +
                "Status: %s, URL: %s, Response: %s", 
                propertyId, e.getStatusCode(), url, e.getResponseBodyAsString()
            );
            throw new RuntimeException("Property-service client error: " + e.getStatusCode(), e);
        } catch (HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            
            String detailedError = String.format(
                "Property-service server error (%s) for property '%s'. " +
                "\n\n" +
                "DIAGNOSIS: The property exists in the database, but property-service failed to load it. " +
                "This is usually caused by one of these issues:\n" +
                "1) Missing or NULL relationship data:\n" +
                "   - PropertyType (type_id) is NULL or references non-existent type\n" +
                "   - Address (address_id) is NULL or references non-existent address\n" +
                "   - Check: SELECT type_id, address_id FROM properties WHERE id = '%s';\n" +
                "2) Corrupted relationship data in join tables\n" +
                "3) EntityGraph loading failure when fetching relationships\n" +
                "\n" +
                "SOLUTION: Check property-service logs for the exact error. " +
                "Then verify the property data in database:\n" +
                "  SELECT id, type_id, address_id, user_id, daily_price, deposit_amount, status " +
                "  FROM properties WHERE id = '%s';\n" +
                "\n" +
                "Response from property-service: %s",
                e.getStatusCode(), propertyId, propertyId, propertyId,
                responseBody != null && responseBody.length() > 300 
                    ? responseBody.substring(0, 300) + "..." 
                    : (responseBody != null ? responseBody : "No details available")
            );
            throw new RuntimeException(detailedError, e);
        } catch (ResourceAccessException e) {
            String errorMsg = String.format(
                "Failed to connect to property-service at %s. " +
                "Please ensure property-service is running and accessible. " +
                "Error: %s", url, e.getMessage()
            );
            throw new RuntimeException(errorMsg, e);
        } catch (RestClientException e) {
            String errorMsg = String.format(
                "Error calling property-service API for property %s: %s", 
                propertyId, e.getMessage()
            );
            throw new RuntimeException(errorMsg, e);
        }
    }

    public String extractOwnerUserId(Map<String, Object> propertyResponse) {
        if (propertyResponse == null) {
            return null;
        }

        Object userIdObj = propertyResponse.get("userId");
        if (userIdObj != null) {
            return userIdObj.toString();
        }

        return null;
    }

    public String extractPropertyTitle(Map<String, Object> propertyResponse, String propertyId) {
        if (propertyResponse == null) {
            return "Property " + propertyId;
        }

        Object titleObj = propertyResponse.get("title");
        if (titleObj != null) {
            return titleObj.toString();
        }

        return "Property " + propertyId;
    }

    public Double extractDailyPrice(Map<String, Object> propertyResponse) {
        if (propertyResponse == null) {
            return 0.0;
        }

        Object priceObj = propertyResponse.get("dailyPrice");
        if (priceObj instanceof Number) {
            return ((Number) priceObj).doubleValue();
        }

        return 0.0;
    }

    public Double extractDepositAmount(Map<String, Object> propertyResponse) {
        if (propertyResponse == null) {
            return 0.0;
        }

        Object depositObj = propertyResponse.get("depositAmount");
        if (depositObj instanceof Number) {
            return ((Number) depositObj).doubleValue();
        }

        return 0.0;
    }
}

