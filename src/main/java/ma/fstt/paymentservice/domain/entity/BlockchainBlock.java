package ma.fstt.paymentservice.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigInteger;
import java.sql.Timestamp;

@Entity
@Table(name = "blockchain_blocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainBlock {
    @Id
    private String id; // e.g. "PAYMENT_SERVICE"

    private BigInteger lastProcessedBlock;

    @UpdateTimestamp
    private Timestamp updatedAt;
}
