package ma.fstt.paymentservice.core.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class BookingCreatedConsumer {
    private final BlockingQueue<Long> bookingIdQueue = new LinkedBlockingQueue<>();
    private volatile Long lastReceivedBookingId = null;

    @RabbitListener(queues = "booking.created")
    public void handleBookingCreated(BookingCreatedMessage message) {
        try {
            Long bookingId = message.getBookingId();
            if (bookingId == null) {
                return;
            }

            lastReceivedBookingId = bookingId;
            bookingIdQueue.offer(bookingId);
        } catch (Exception e) {
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
        private Long propertyId;
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

        public Long getPropertyId() {
            return propertyId;
        }

        public void setPropertyId(Long propertyId) {
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

