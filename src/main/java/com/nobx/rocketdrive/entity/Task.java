package com.nobx.rocketdrive.entity;

import java.time.LocalDateTime;

import com.nobx.rocketdrive.enums.CloudServiceEnum;
import com.nobx.rocketdrive.enums.CloudTypeEnum;
import com.nobx.rocketdrive.enums.TaskStatusEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "backend_task")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private CloudTypeEnum cloudType;

    @Column(name = "source_path", nullable = false)
    private String sourcePath;

    @Column(name = "destination_path")
    private String destinationPath;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "cloud_service", nullable = false)
    private CloudServiceEnum cloudService;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatusEnum status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "source_access_token", length = 2000)
    private String sourceAccessToken;
    
    @Column(name = "destination_access_token", length = 2000)
    private String destinationAccessToken;
    
    @Column(name = "source_account_id", nullable = false)
    private Integer sourceAccountId;
    
    @Column(name = "destination_account_id", nullable = false)
    private Integer destinationAccountId;

    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "source_email", nullable = false)
    private String sourceEmail;
    
    @Column(name = "destination_email", nullable = false)
    private String destinationEmail;


    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


}
