package com.vinncorp.orga.admin;

import com.vinncorp.orga.tenant.Tenant;
import com.vinncorp.orga.tenant.TenantRepository;
import com.vinncorp.orga.user.User;
import com.vinncorp.orga.user.UserRepository;
import com.vinncorp.orga.user.User.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/super")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminController(TenantRepository tenantRepository, 
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String domain = request.get("domain");

        if (tenantRepository.findByDomain(domain).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Domain already exists"));
        }

        Tenant tenant = new Tenant(name, domain);
        tenant = tenantRepository.save(tenant);

        return ResponseEntity.ok(Map.of(
                "id", tenant.getId(),
                "name", tenant.getName(),
                "domain", tenant.getDomain(),
                "message", "Tenant created successfully"
        ));
    }

    @GetMapping("/tenants")
    public ResponseEntity<?> getAllTenants() {
        List<Tenant> tenants = tenantRepository.findAll();

        List<Map<String, Object>> result = tenants.stream()
                .map(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", t.getId());
                    map.put("name", t.getName());
                    map.put("domain", t.getDomain());
                    map.put("createdAt", t.getCreatedAt().toString());
                    map.put("isActive", t.getIsActive());
                    map.put("subscriptionPlan", t.getSubscriptionPlan());
                    map.put("maxUsers", t.getMaxUsers());
                    map.put("maxMessagesPerMonth", t.getMaxMessagesPerMonth());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PutMapping("/tenants/{tenantId}")
    public ResponseEntity<?> updateTenant(@PathVariable Long tenantId,
                                         @RequestBody Map<String, Object> request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        if (request.containsKey("name")) {
            tenant.setName((String) request.get("name"));
        }
        if (request.containsKey("isActive")) {
            tenant.setIsActive((Boolean) request.get("isActive"));
        }
        if (request.containsKey("maxUsers")) {
            tenant.setMaxUsers(((Number) request.get("maxUsers")).intValue());
        }
        if (request.containsKey("maxMessagesPerMonth")) {
            tenant.setMaxMessagesPerMonth(((Number) request.get("maxMessagesPerMonth")).longValue());
        }
        if (request.containsKey("subscriptionPlan")) {
            tenant.setSubscriptionPlan((String) request.get("subscriptionPlan"));
        }

        tenantRepository.save(tenant);
        return ResponseEntity.ok(Map.of("message", "Tenant updated successfully"));
    }

    @DeleteMapping("/tenants/{tenantId}")
    public ResponseEntity<?> deleteTenant(@PathVariable Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        tenantRepository.delete(tenant);
        return ResponseEntity.ok(Map.of("message", "Tenant deleted successfully"));
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> getSystemMetrics() {
        long totalTenants = tenantRepository.count();
        long totalUsers = userRepository.count();
        long activeTenants = tenantRepository.findAll().stream()
                .filter(Tenant::getIsActive)
                .count();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalTenants", totalTenants);
        metrics.put("activeTenants", activeTenants);
        metrics.put("totalUsers", totalUsers);

        return ResponseEntity.ok(metrics);
    }

    @PostMapping("/tenants/{tenantId}/admin")
    public ResponseEntity<?> createTenantAdmin(@PathVariable Long tenantId,
                                               @RequestBody Map<String, String> request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        String email = request.get("email");
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email already in use"));
        }

        String password = request.get("password");
        String hashed = passwordEncoder.encode(password);
        User admin = new User(email, hashed, tenant);
        admin.setRole(UserRole.TENANT_ADMIN);
        userRepository.save(admin);

        return ResponseEntity.ok(Map.of("message", "Tenant admin created successfully", "userId", admin.getId()));
    }

    @PutMapping("/users/{userId}/assign-tenant")
    public ResponseEntity<?> assignUserToTenant(@PathVariable Long userId,
                                                @RequestBody Map<String, Object> request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long tenantId = ((Number) request.get("tenantId")).longValue();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        // Check if user is SUPER_ADMIN - don't allow reassigning super admins
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cannot reassign super admin"));
        }

        user.setTenant(tenant);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "User assigned to tenant successfully",
                "userId", user.getId(),
                "tenantId", tenant.getId(),
                "tenantName", tenant.getName()
        ));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        List<User> users = userRepository.findAll();

        List<Map<String, Object>> result = users.stream()
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("email", u.getEmail());
                    map.put("role", u.getRole().name());
                    map.put("tenantId", u.getTenant().getId());
                    map.put("tenantName", u.getTenant().getName());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");
        Long tenantId = Long.parseLong(request.get("tenantId"));
        String roleStr = request.getOrDefault("role", "EMPLOYEE");

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email already in use"));
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        UserRole role;
        try {
            role = UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid role"));
        }

        // Only allow creating EMPLOYEE or TENANT_ADMIN, not SUPER_ADMIN
        if (role == UserRole.SUPER_ADMIN) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cannot create super admin via this endpoint"));
        }

        String hashed = passwordEncoder.encode(password);
        User user = new User(email, hashed, tenant);
        user.setRole(role);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "User created successfully",
                "userId", user.getId(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "tenantId", tenant.getId()
        ));
    }
}

