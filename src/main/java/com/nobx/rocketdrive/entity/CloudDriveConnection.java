package com.nobx.rocketdrive.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "backend_clouddriveconnection")
public class CloudDriveConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_token", columnDefinition = "LONGTEXT")
    private String accessToken;

    @Column(name = "refresh_token", length = 2550)
    private String refreshToken;
    
    @Column(name = "expiry_time")
    private Long expiryTime;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "email", length = 254, nullable = false)
    private String email;

    @Column(name = "provider", length = 50, nullable = false)
    private String provider;
    
    @Column(name = "tenant_id")
    private  String tenantId;

 
}
