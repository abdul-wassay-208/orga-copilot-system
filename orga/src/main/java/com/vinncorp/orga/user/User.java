package com.vinncorp.orga.user;

import com.vinncorp.orga.tenant.Tenant;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.EMPLOYEE;

    public enum UserRole {
        EMPLOYEE, TENANT_ADMIN, SUPER_ADMIN
    }

    public User() {
    }

    public User(String email, String passwordHash, Tenant tenant) {
        this.email = email;
        this.fullName = email.split("@")[0]; // Default to email username if fullName not provided
        this.passwordHash = passwordHash;
        this.tenant = tenant;
    }

    public User(String email, String fullName, String passwordHash, Tenant tenant) {
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.tenant = tenant;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}


