package ma.fstt.paymentservice.api.controller;

import lombok.RequiredArgsConstructor;
import ma.fstt.paymentservice.domain.entity.UserAccount;
import ma.fstt.paymentservice.domain.repository.UserAccountRepository;
import ma.fstt.paymentservice.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountRepository userAccountRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable Long userId) {
        try {
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + userId));

            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("firstName", user.getFirstName());
            response.put("lastName", user.getLastName());
            response.put("email", user.getEmail());
            response.put("walletAddress", user.getWalletAddress());
            response.put("score", user.getScore());
            int penaltyPointsDeducted = 100 - (user.getScore() != null ? user.getScore() : 100);
            response.put("penaltyPoints", penaltyPointsDeducted);
            response.put("isSuspended", user.getIsSuspended() != null ? user.getIsSuspended() : false);
            response.put("suspensionReason", user.getSuspensionReason());
            response.put("suspensionUntil", user.getSuspensionUntil());

            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/{userId}/penalty")
    @Transactional
    public ResponseEntity<Map<String, Object>> addPenaltyPoints(
            @PathVariable Long userId,
            @RequestBody PenaltyPointsRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            UserAccount user = userAccountRepository.findById(userId).orElse(null);
            
            if (user == null) {
                response.put("status", "error");
                response.put("message", "User not found in payment-service database. User ID: " + userId);
                response.put("errorCode", "USER_NOT_FOUND_IN_PAYMENT_SERVICE");
                return ResponseEntity.badRequest().body(response);
            }

            int currentScore = user.getScore() != null ? user.getScore() : 100;
            int penaltyPoints = request.getPenaltyPoints() != null ? request.getPenaltyPoints() : 0;
            
            if (penaltyPoints <= 0) {
                response.put("status", "error");
                response.put("message", "Invalid penalty points: " + penaltyPoints);
                return ResponseEntity.badRequest().body(response);
            }
            
            int newScore = Math.max(0, currentScore - penaltyPoints);
            user.setScore(newScore);

            checkAndApplySuspension(user);

            UserAccount savedUser = userAccountRepository.save(user);
            userAccountRepository.flush();
            
            UserAccount verifyUser = userAccountRepository.findById(userId).orElse(null);

            response.put("status", "success");
            response.put("message", "Penalty points deducted from score successfully");
            response.put("userId", userId);
            response.put("previousScore", currentScore);
            response.put("newScore", newScore);
            response.put("penaltyPointsDeducted", penaltyPoints);
            response.put("isSuspended", savedUser.getIsSuspended());
            response.put("verifiedScore", verifyUser != null ? verifyUser.getScore() : null);

            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to add penalty points: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/{userId}/suspend")
    public ResponseEntity<Map<String, Object>> suspendUser(
            @PathVariable Long userId,
            @RequestBody SuspendUserRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + userId));

            user.setIsSuspended(true);
            user.setSuspensionReason(request.getReason());
            if (request.getSuspensionDays() != null && request.getSuspensionDays() > 0) {
                user.setSuspensionUntil(LocalDateTime.now().plusDays(request.getSuspensionDays()));
            } else {
                user.setSuspensionUntil(null);
            }

            userAccountRepository.save(user);

            response.put("status", "success");
            response.put("message", "User suspended successfully");
            response.put("userId", userId);
            response.put("suspensionUntil", user.getSuspensionUntil());

            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to suspend user: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/{userId}/unsuspend")
    public ResponseEntity<Map<String, Object>> unsuspendUser(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + userId));

            user.setIsSuspended(false);
            user.setSuspensionReason(null);
            user.setSuspensionUntil(null);

            userAccountRepository.save(user);

            response.put("status", "success");
            response.put("message", "User unsuspended successfully");
            response.put("userId", userId);

            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to unsuspend user: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private void checkAndApplySuspension(UserAccount user) {
        int score = user.getScore() != null ? user.getScore() : 100;
        int pointsDeducted = 100 - score;

        if (score <= 74) {
            user.setIsSuspended(true);
            user.setSuspensionReason("Score too low (≤74) - " + pointsDeducted + " penalty points deducted");
            user.setSuspensionUntil(null);
        } else if (score <= 79) {
            user.setIsSuspended(true);
            user.setSuspensionReason("Low score (75-79) - " + pointsDeducted + " penalty points deducted");
            user.setSuspensionUntil(LocalDateTime.now().plusDays(60));
        } else if (score <= 84) {
            user.setIsSuspended(true);
            user.setSuspensionReason("Low score (80-84) - " + pointsDeducted + " penalty points deducted");
            user.setSuspensionUntil(LocalDateTime.now().plusDays(30));
        } else if (score <= 89) {
            user.setIsSuspended(true);
            user.setSuspensionReason("Moderate score (85-89) - " + pointsDeducted + " penalty points deducted");
            user.setSuspensionUntil(LocalDateTime.now().plusDays(7));
        } else if (score > 89 && user.getIsSuspended() != null && user.getIsSuspended()) {
            if (user.getSuspensionUntil() != null && LocalDateTime.now().isAfter(user.getSuspensionUntil())) {
                user.setIsSuspended(false);
                user.setSuspensionReason(null);
                user.setSuspensionUntil(null);
            }
        }
    }

    @lombok.Data
    private static class PenaltyPointsRequest {
        private Integer penaltyPoints;
    }

    @lombok.Data
    private static class SuspendUserRequest {
        private String reason;
        private Integer suspensionDays; // null for permanent suspension
    }
}

