package ma.fstt.paymentservice.core.service;

import lombok.RequiredArgsConstructor;
import ma.fstt.paymentservice.domain.entity.Property;
import ma.fstt.paymentservice.domain.repository.PropertyRepository;
import ma.fstt.paymentservice.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PropertyDatabaseService {

    private final PropertyRepository propertyRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getPropertyById(String propertyId) {
        if (propertyId == null || propertyId.trim().isEmpty()) {
            throw new IllegalArgumentException("Property ID cannot be null or empty");
        }

        try {
            Property property = propertyRepository.findById(propertyId)
                    .orElseThrow(() -> {
                        return new BusinessException("PROPERTY_NOT_FOUND", 
                            "Property not found in database with id: " + propertyId);
                    });

            Map<String, Object> propertyMap = new HashMap<>();
            propertyMap.put("id", property.getId());
            propertyMap.put("userId", property.getUserId());
            propertyMap.put("dailyPrice", property.getDailyPrice());
            propertyMap.put("depositAmount", property.getDepositAmount() != null ? property.getDepositAmount() : 0.0);
            propertyMap.put("title", property.getTitle());
            propertyMap.put("description", property.getDescription());
            propertyMap.put("capacity", property.getCapacity());
            
            Integer bedrooms = property.getNumberOfBedrooms() != null ? property.getNumberOfBedrooms() : property.getBedrooms();
            Integer bathrooms = property.getNumberOfBathrooms() != null ? property.getNumberOfBathrooms() : property.getBathrooms();
            propertyMap.put("numberOfBedrooms", bedrooms);
            propertyMap.put("numberOfBathrooms", bathrooms);
            propertyMap.put("numberOfBeds", property.getNumberOfBeds());
            
            propertyMap.put("address", property.getAddress());
            propertyMap.put("city", property.getCity());
            propertyMap.put("country", property.getCountry());
            propertyMap.put("latitude", property.getLatitude());
            propertyMap.put("longitude", property.getLongitude());
            
            boolean isNegotiable = property.getNegotiationPercentage() != null && property.getNegotiationPercentage() > 0;
            propertyMap.put("isNegotiable", isNegotiable);
            propertyMap.put("negotiationPercentage", property.getNegotiationPercentage());
            propertyMap.put("maxNegotiationPercent", property.getMaxNegotiationPercent());
            
            propertyMap.put("isAvailable", true);
            propertyMap.put("discountEnabled", false);

            return propertyMap;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("DATABASE_ERROR", 
                "Failed to fetch property from database: " + e.getMessage());
        }
    }

    public String extractOwnerUserId(Map<String, Object> propertyMap) {
        if (propertyMap == null) {
            return null;
        }
        
        Object userId = propertyMap.get("userId");
        if (userId == null) {
            return null;
        }
        
        return userId.toString();
    }

    public Double extractDailyPrice(Map<String, Object> propertyMap) {
        if (propertyMap == null) {
            return null;
        }
        
        Object dailyPrice = propertyMap.get("dailyPrice");
        if (dailyPrice == null) {
            return null;
        }
        
        if (dailyPrice instanceof Number) {
            return ((Number) dailyPrice).doubleValue();
        }
        
        try {
            return Double.parseDouble(dailyPrice.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Double extractDepositAmount(Map<String, Object> propertyMap) {
        if (propertyMap == null) {
            return 0.0;
        }
        
        Object depositAmount = propertyMap.get("depositAmount");
        if (depositAmount == null) {
            return 0.0;
        }
        
        if (depositAmount instanceof Number) {
            return ((Number) depositAmount).doubleValue();
        }
        
        try {
            return Double.parseDouble(depositAmount.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}

