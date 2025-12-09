package ma.fstt.paymentservice.domain.repository;

import ma.fstt.paymentservice.domain.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByPropertyId(String propertyId);
    
    @Query("SELECT b FROM Booking b WHERE b.propertyId = :propertyId " +
           "AND b.id != :excludeBookingId " +
           "AND b.checkInDate <= :checkOutDate " +
           "AND b.checkOutDate >= :checkInDate " +
           "AND b.status NOT IN ('COMPLETED', 'CANCELLED')")
    List<Booking> findOverlappingBookings(
        @Param("propertyId") String propertyId,
        @Param("excludeBookingId") Long excludeBookingId,
        @Param("checkInDate") LocalDate checkInDate,
        @Param("checkOutDate") LocalDate checkOutDate
    );
}

