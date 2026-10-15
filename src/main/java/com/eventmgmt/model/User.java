package com.eventmgmt.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean enabled = true;

    private String phone;

    private String city;

    // Razorpay Route linked account. Approval must be granted by an admin after checking onboarding.
    @Column(length = 80)
    private String razorpayRouteAccountId;

    private boolean razorpayRouteApproved = false;

    // Organizer-owned UPI ID used for direct UPI payments.
    @Column(length = 120)
    private String upiId;

    @Column(length = 500)
    private String preferences;

    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getRazorpayRouteAccountId() { return razorpayRouteAccountId; }
    public void setRazorpayRouteAccountId(String razorpayRouteAccountId) { this.razorpayRouteAccountId = razorpayRouteAccountId; }
    public boolean isRazorpayRouteApproved() { return razorpayRouteApproved; }
    public void setRazorpayRouteApproved(boolean razorpayRouteApproved) { this.razorpayRouteApproved = razorpayRouteApproved; }
    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }
    public String getPreferences() { return preferences; }
    public void setPreferences(String preferences) { this.preferences = preferences; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
