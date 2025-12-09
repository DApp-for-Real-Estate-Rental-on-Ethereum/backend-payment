package ma.fstt.paymentservice.domain.repository;

import ma.fstt.paymentservice.domain.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<Property, String> {
    Optional<Property> findById(String id);
}

