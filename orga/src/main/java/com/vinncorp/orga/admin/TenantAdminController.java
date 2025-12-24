package com.vinncorp.orga.admin;

import com.vinncorp.orga.chat.MessageRepository;
import com.vinncorp.orga.tenant.Tenant;
import com.vinncorp.orga.tenant.TenantRepository;
import com.vinncorp.orga.user.User;
import com.vinncorp.orga.user.UserRepository;
import com.vinncorp.orga.user.User.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/tenant")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantAdminController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageRepository messageRepository;

    public TenantAdminController(UserRepository userRepository,
                                TenantRepository tenantRepository,
                                PasswordEncoder passwordEncoder,
                                MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.messageRepository = messageRepository;
    }

    private Tenant getCurrentUserTenant(UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getTenant();
    }

    @PostMapping("/users/invite")
    public ResponseEntity<?> inviteUser(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestBody Map<String, String> request) {
        Tenant tenant = getCurrentUserTenant(userDetails);
        
        // Check user limit
        long currentUserCount = userRepository.count();
        if (currentUserCount >= tenant.getMaxUsers()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "User limit reached. Max users: " + tenant.getMaxUsers()));
        }

        String email = request.get("email");
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email already in use"));
        }

        String password = request.getOrDefault("password", "TempPassword123!");
        String fullName = request.getOrDefault("fullName", email.split("@")[0]);
        String roleStr = request.getOrDefault("role", "EMPLOYEE");
        String hashed = passwordEncoder.encode(password);
        
        User newUser = new User(email, fullName, hashed, tenant);
        
        // Set role based on request, default to EMPLOYEE
        try {
            UserRole role = UserRole.valueOf(roleStr);
            // Only allow EMPLOYEE or TENANT_ADMIN, not SUPER_ADMIN
            if (role == UserRole.SUPER_ADMIN) {
                role = UserRole.EMPLOYEE;
            }
            newUser.setRole(role);
        } catch (IllegalArgumentException e) {
            newUser.setRole(UserRole.EMPLOYEE);
        }
        
        userRepository.save(newUser);

        return ResponseEntity.ok(Map.of("message", "User invited successfully", "userId", newUser.getId()));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> removeUser(@AuthenticationPrincipal UserDetails userDetails,
                                       @PathVariable Long userId) {
        Tenant tenant = getCurrentUserTenant(userDetails);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!user.getTenant().getId().equals(tenant.getId())) {
            return ResponseEntity.status(403).body(Map.of("message", "Access denied"));
        }

        if (user.getRole() == UserRole.TENANT_ADMIN) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cannot remove tenant admin"));
        }

        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "User removed successfully"));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(@AuthenticationPrincipal UserDetails userDetails) {
        Tenant tenant = getCurrentUserTenant(userDetails);
        
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getTenant().getId().equals(tenant.getId()))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = users.stream()
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("email", u.getEmail());
                    map.put("fullName", u.getFullName());
                    map.put("role", u.getRole().name());
                    // Calculate message count for this user (current month)
                    YearMonth currentMonth = YearMonth.now();
                    LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();
                    LocalDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59);
                    long messageCount = messageRepository.findAll().stream()
                            .filter(m -> {
                                User msgUser = m.getConversation().getUser();
                                return msgUser.getId().equals(u.getId()) &&
                                       m.getCreatedAt().isAfter(startOfMonth) &&
                                       m.getCreatedAt().isBefore(endOfMonth);
                            })
                            .count();
                    map.put("messagesUsed", messageCount);
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/usage/metrics")
    public ResponseEntity<?> getUsageMetrics(@AuthenticationPrincipal UserDetails userDetails) {
        Tenant tenant = getCurrentUserTenant(userDetails);
        
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59);

        // Count messages in current month (numbers only, no content)
        long messageCount = messageRepository.findAll().stream()
                .filter(m -> {
                    User msgUser = m.getConversation().getUser();
                    return msgUser.getTenant().getId().equals(tenant.getId()) &&
                           m.getCreatedAt().isAfter(startOfMonth) &&
                           m.getCreatedAt().isBefore(endOfMonth);
                })
                .count();

        long userCount = userRepository.findAll().stream()
                .filter(u -> u.getTenant().getId().equals(tenant.getId()))
                .count();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("messagesThisMonth", messageCount);
        metrics.put("maxMessagesPerMonth", tenant.getMaxMessagesPerMonth());
        metrics.put("currentUsers", userCount);
        metrics.put("maxUsers", tenant.getMaxUsers());
        metrics.put("subscriptionPlan", tenant.getSubscriptionPlan());

        return ResponseEntity.ok(metrics);
    }

    @PutMapping("/limits")
    public ResponseEntity<?> updateLimits(@AuthenticationPrincipal UserDetails userDetails,
                                         @RequestBody Map<String, Object> request) {
        Tenant tenant = getCurrentUserTenant(userDetails);
        
        if (request.containsKey("maxUsers")) {
            tenant.setMaxUsers(((Number) request.get("maxUsers")).intValue());
        }
        if (request.containsKey("maxMessagesPerMonth")) {
            tenant.setMaxMessagesPerMonth(((Number) request.get("maxMessagesPerMonth")).longValue());
        }
        
        tenantRepository.save(tenant);
        return ResponseEntity.ok(Map.of("message", "Limits updated successfully"));
    }

    @PutMapping("/subscription")
    public ResponseEntity<?> updateSubscription(@AuthenticationPrincipal UserDetails userDetails,
                                              @RequestBody Map<String, String> request) {
        Tenant tenant = getCurrentUserTenant(userDetails);
        
        String plan = request.get("plan");
        if (plan != null && (plan.equals("BASIC") || plan.equals("PRO") || plan.equals("ENTERPRISE"))) {
            tenant.setSubscriptionPlan(plan);
            tenantRepository.save(tenant);
            return ResponseEntity.ok(Map.of("message", "Subscription updated successfully"));
        }
        
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid subscription plan"));
    }

    @PostMapping("/knowledge-base")
    public ResponseEntity<?> uploadKnowledgeBase(@AuthenticationPrincipal UserDetails userDetails,
                                                @RequestBody Map<String, String> request) {
        // This is a placeholder - actual knowledge base implementation would store files/content
        getCurrentUserTenant(userDetails); // Verify tenant access
        
        // In a real implementation, you would save this to a file storage or database
        // For now, we'll just return success
        
        return ResponseEntity.ok(Map.of("message", "Knowledge base updated successfully"));
    }
}

