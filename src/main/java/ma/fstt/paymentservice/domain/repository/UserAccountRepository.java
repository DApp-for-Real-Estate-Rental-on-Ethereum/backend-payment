package ma.fstt.paymentservice.domain.repository;

import ma.fstt.paymentservice.domain.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    // Using findById from JpaRepository instead of findByCurrentUserId
    // since UserAccount now uses 'id' directly
}


