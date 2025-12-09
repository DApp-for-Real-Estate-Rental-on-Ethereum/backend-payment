package ma.fstt.paymentservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class PaymentsMetrics {

    private final AtomicLong listenerLagBlocks = new AtomicLong(0);
    private final MeterRegistry registry;

    public PaymentsMetrics(MeterRegistry registry) {
        this.registry = registry;

        Counter.builder("payments_onchain_events_total")
                .description("Total number of on-chain events processed")
                .tag("type", "unknown")
                .register(registry);

        Counter.builder("payments_tx_status_total")
                .description("Total transactions by status")
                .tag("status", "unknown")
                .register(registry);

        Gauge.builder("payments_listener_lag_blocks", listenerLagBlocks, AtomicLong::get)
                .description("Number of blocks the listener is behind")
                .register(registry);
    }

    public void incrementOnchainEvent(String eventType) {
        Counter.builder("payments_onchain_events_total")
                .description("Total number of on-chain events processed")
                .tag("type", eventType)
                .register(registry)
                .increment();
    }

    public void incrementTxStatus(String status) {
        Counter.builder("payments_tx_status_total")
                .description("Total transactions by status")
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public void updateListenerLag(long lagBlocks) {
        listenerLagBlocks.set(lagBlocks);
    }
}

