package ma.fstt.paymentservice.domain.repository;

import ma.fstt.paymentservice.domain.entity.BlockchainBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlockchainBlockRepository extends JpaRepository<BlockchainBlock, String> {
}
