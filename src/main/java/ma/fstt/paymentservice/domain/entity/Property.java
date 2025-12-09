package ma.fstt.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "properties")
@Getter
@Setter
@ToString
public class Property {

    @Id
    @Column(name = "id", length = 255)
    private String id;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "daily_price", nullable = false)
    private Double dailyPrice;
    
    @Column(name = "deposit_amount", nullable = false, columnDefinition = "DOUBLE PRECISION DEFAULT 0.0")
    private Double depositAmount;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", length = 255, nullable = false)
    private String description;

    @Column(name = "bedrooms", nullable = true)
    private Integer bedrooms;

    @Column(name = "bathrooms", nullable = true)
    private Integer bathrooms;

    @Column(name = "number_of_bedrooms", nullable = false, columnDefinition = "INTEGER DEFAULT 1")
    private Integer numberOfBedrooms;

    @Column(name = "number_of_bathrooms", nullable = false, columnDefinition = "INTEGER DEFAULT 1")
    private Integer numberOfBathrooms;

    @Column(name = "number_of_beds", nullable = false, columnDefinition = "INTEGER DEFAULT 1")
    private Integer numberOfBeds;

    @Column(name = "address", length = 255, nullable = true)
    private String address;

    @Column(name = "city", length = 120, nullable = true)
    private String city;

    @Column(name = "country", length = 120, nullable = true)
    private String country;

    @Column(name = "latitude", nullable = true)
    private Double latitude;

    @Column(name = "longitude", nullable = true)
    private Double longitude;

    @Column(name = "max_negotiation_percent", nullable = true)
    private Integer maxNegotiationPercent;

    @Column(name = "negotiation_percentage", nullable = false)
    private Double negotiationPercentage;

    @Column(name = "price", nullable = false)
    private Double price;

    @Column(name = "status", length = 255, nullable = false)
    private String status;

    @Transient
    private Boolean isAvailable = true;

    @Transient
    private Boolean isDeleted = false;

    @Transient
    private Boolean isNegotiable = false;

    @Transient
    private Boolean discountEnabled = false;
}

