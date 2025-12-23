package com.vinncorp.orga.admin;

import com.vinncorp.orga.tenant.Tenant;
import com.vinncorp.orga.tenant.TenantRepository;
import com.vinncorp.orga.user.User;
import com.vinncorp.orga.user.UserRepository;
import com.vinncorp.orga.user.User.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper controller to create admin users for testing
 * This should be removed or secured in production
 */
@RestController
@RequestMapping("/api/admin/setup")
public class AdminSetupController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSetupController(UserRepository userRepository,
                               TenantRepository tenantRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/create-tenant-admin")
    @Transactional
    public ResponseEntity<?> createTenantAdmin(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.getOrDefault("password", "admin123");
        String tenantName = request.getOrDefault("tenantName", "Test Organization");
        String tenantDomain = request.getOrDefault("tenantDomain", "test");

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
        }

        Tenant tenant = tenantRepository.findByDomain(tenantDomain)
                .orElseGet(() -> {
                    Tenant newTenant = new Tenant(tenantName, tenantDomain);
                    return tenantRepository.save(newTenant);
                });

        String hashed = passwordEncoder.encode(password);
        User admin = new User(email, hashed, tenant);
        admin.setRole(UserRole.TENANT_ADMIN);
        userRepository.save(admin);

        return ResponseEntity.ok(Map.of(
                "message", "Tenant admin created successfully",
                "email", email,
                "password", password,
                "role", "TENANT_ADMIN"
        ));
    }

    @GetMapping("/test-db")
    public ResponseEntity<?> testDatabase() {
        try {
            long userCount = userRepository.count();
            long tenantCount = tenantRepository.count();
            
            List<Map<String, Object>> allUsers = userRepository.findAll().stream()
                    .map(u -> {
                        Map<String, Object> userMap = new java.util.HashMap<>();
                        userMap.put("id", u.getId());
                        userMap.put("email", u.getEmail());
                        userMap.put("role", u.getRole().name());
                        userMap.put("tenantId", u.getTenant().getId());
                        return userMap;
                    })
                    .collect(Collectors.toList());
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("message", "Database connection successful");
            response.put("userCount", userCount);
            response.put("tenantCount", tenantCount);
            response.put("users", allUsers);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("message", "Database error: " + e.getMessage());
            errorResponse.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping("/create-super-admin")
    @Transactional
    public ResponseEntity<?> createSuperAdmin(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String password = request.getOrDefault("password", "admin123");
            String tenantName = request.getOrDefault("tenantName", "Platform");
            String tenantDomain = request.getOrDefault("tenantDomain", "platform");

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }

            if (userRepository.existsByEmail(email)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
            }

            Tenant tenant = tenantRepository.findByDomain(tenantDomain)
                    .orElseGet(() -> {
                        Tenant newTenant = new Tenant(tenantName, tenantDomain);
                        return tenantRepository.save(newTenant);
                    });

            String hashed = passwordEncoder.encode(password);
            User admin = new User(email, hashed, tenant);
            admin.setRole(UserRole.SUPER_ADMIN);
            
            // Save and verify
            User savedAdmin = userRepository.save(admin);
            
            // Verify the user was saved
            if (savedAdmin.getId() == null) {
                return ResponseEntity.status(500).body(Map.of("message", "Failed to save super admin - ID is null"));
            }
            
            // Verify the role was saved correctly
            User verifyUser = userRepository.findById(savedAdmin.getId())
                    .orElseThrow(() -> new RuntimeException("User was not found after save"));
            
            if (verifyUser.getRole() != UserRole.SUPER_ADMIN) {
                return ResponseEntity.status(500).body(Map.of(
                        "message", "Role was not saved correctly",
                        "expected", "SUPER_ADMIN",
                        "actual", verifyUser.getRole().name()
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Super admin created successfully",
                    "userId", savedAdmin.getId(),
                    "email", email,
                    "password", password,
                    "role", "SUPER_ADMIN",
                    "tenantId", tenant.getId(),
                    "tenantName", tenant.getName()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Error creating super admin: " + e.getMessage(),
                    "error", e.getClass().getSimpleName()
            ));
        }
    }
}

