package ma.fstt.paymentservice.core.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@RequiredArgsConstructor
public class BookingCreatedConsumer {
    private final BlockingQueue<Long> bookingIdQueue = new LinkedBlockingQueue<>();
    private volatile Long lastReceivedBookingId = null;

    private final ma.fstt.paymentservice.domain.repository.BookingRepository bookingRepository;

    @RabbitListener(queues = "booking.created")
    public void handleBookingCreated(BookingCreatedMessage message) {
        try {
            Long bookingId = message.getBookingId();
            if (bookingId == null) {
                return;
            }

            // Save to Database
            ma.fstt.paymentservice.domain.entity.Booking booking = bookingRepository.findById(bookingId)
                    .orElse(new ma.fstt.paymentservice.domain.entity.Booking());

            booking.setId(bookingId);
            booking.setUserId(message.getTenantId());
            booking.setPropertyId(message.getPropertyId());
            if (message.getFinalRentAmount() != null) {
                booking.setTotalPrice(message.getFinalRentAmount().doubleValue());
            }
            // Only update status if it's new or changing (preserving flow)
            if (booking.getStatus() == null) {
                booking.setStatus(message.getStatus() != null ? message.getStatus() : "PENDING_PAYMENT");
            }

            bookingRepository.save(booking);

            lastReceivedBookingId = bookingId;
            bookingIdQueue.offer(bookingId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Long pollBookingId(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        return bookingIdQueue.poll(timeout, unit);
    }

    public Long getLastReceivedBookingId() {
        return lastReceivedBookingId;
    }

    public int getQueueSize() {
        return bookingIdQueue.size();
    }

    public static class BookingCreatedMessage {
        private Long bookingId;
        private Long tenantId;
        private Long ownerId;
        private String propertyId;
        private java.math.BigDecimal finalRentAmount;
        private java.math.BigDecimal depositAmount;
        private String status;

        public Long getBookingId() {
            return bookingId;
        }

        public void setBookingId(Long bookingId) {
            this.bookingId = bookingId;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public Long getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(Long ownerId) {
            this.ownerId = ownerId;
        }

        public String getPropertyId() {
            return propertyId;
        }

        public void setPropertyId(String propertyId) {
            this.propertyId = propertyId;
        }

        public java.math.BigDecimal getFinalRentAmount() {
            return finalRentAmount;
        }

        public void setFinalRentAmount(java.math.BigDecimal finalRentAmount) {
            this.finalRentAmount = finalRentAmount;
        }

        public java.math.BigDecimal getDepositAmount() {
            return depositAmount;
        }

        public void setDepositAmount(java.math.BigDecimal depositAmount) {
            this.depositAmount = depositAmount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
