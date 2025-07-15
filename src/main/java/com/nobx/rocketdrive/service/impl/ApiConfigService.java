package com.nobx.rocketdrive.service.impl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Component
@Getter
public class ApiConfigService {

    // Google Drive
    @Value("${google.drive.api.key}")
    private String googleDriveApiKey;

    @Value("${google.drive.app.secret}")
    private String googleDriveAppSecret;

    // Dropbox
    @Value("${dropbox.api.key}")
    private String dropboxApiKey;

    @Value("${dropbox.app.secret}")
    private String dropboxAppSecret;
    
    //OneDrive
    @Value("${onedrive.api.key}")
    private String oneDriveApiKey;
    
    @Value("${onedrive.app.secret}")
    private String oneDriveAppSecret;
    
    @Value("$(onedrive.tenant.id)")
    private String tenantId;


}

