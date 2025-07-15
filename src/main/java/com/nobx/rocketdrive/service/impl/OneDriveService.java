package com.nobx.rocketdrive.service.impl;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.GraphServiceClient;
import com.nobx.rocketdrive.entity.CloudDriveConnection;
import com.nobx.rocketdrive.entity.Task;
import com.nobx.rocketdrive.repository.CloudDriveConnectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class OneDriveService {

    @Autowired
    private ApiConfigService apiConfigService;

    @Autowired
    private CloudDriveConnectionRepository cloudDriveConnectionRepository;

    private static final String AUTHORITY = "https://login.microsoftonline.com/";
    private static final List<String> SCOPES = Arrays.asList(
        "https://graph.microsoft.com/.default"
    );

    /**
     * Copies a file from one OneDrive account to another using OneDrive's
     * sharing and copy mechanisms with Entra ID authentication.
     *
     * @param task The task containing file operation details.
     */
    public void copyFile(Task task) {
        System.out.println("Starting OneDrive file copy operation...");
        System.out.println("Task details: " + task);
        
        try {
            // Validate task data
            if (task.getSourceAccountId() == null || task.getDestinationAccountId() == null) {
                throw new RuntimeException("Source or destination account ID is null");
            }
            
            System.out.println("Fetching source account connection. Account ID: " + task.getSourceAccountId());
            Optional<CloudDriveConnection> sourceConnection = cloudDriveConnectionRepository.findById(task.getSourceAccountId().longValue());
            if (!sourceConnection.isPresent()) {
                throw new RuntimeException("Source account not found. ID: " + task.getSourceAccountId());
            }
            System.out.println("Source connection found: " + sourceConnection.get().getEmail());

            System.out.println("Fetching destination account connection. Account ID: " + task.getDestinationAccountId());
            Optional<CloudDriveConnection> destConnection = cloudDriveConnectionRepository.findById(task.getDestinationAccountId().longValue());
            if (!destConnection.isPresent()) {
                throw new RuntimeException("Destination account not found. ID: " + task.getDestinationAccountId());
            }
            System.out.println("Destination connection found: " + destConnection.get().getEmail());

            GraphServiceClient<?> sourceClient = getClientForAccount(sourceConnection.get());
            GraphServiceClient<?> destinationClient = getClientForAccount(destConnection.get());

            // Get the source file
            System.out.println("Fetching source file: " + task.getSourcePath());
            DriveItem sourceItem = sourceClient.me()
                .drive()
                .items(task.getSourcePath())
                .buildRequest()
                .get();

            if (sourceItem == null) {
                throw new RuntimeException("Source file not found: " + task.getSourcePath());
            }
            System.out.println("Source file found: " + sourceItem.name);

            // Create sharing link
            System.out.println("Creating sharing link...");
            DriveItemCreateLinkParameterSet parameters = DriveItemCreateLinkParameterSet.newBuilder()
                .withType("view")
                .withScope("organization")
                .build();

            Permission createdPermission = sourceClient.me()
                .drive()
                .items(task.getSourcePath())
                .createLink(parameters)
                .buildRequest()
                .post();

            String sharingUrl = createdPermission.link.webUrl;
            System.out.println("Sharing link created: " + sharingUrl);

            // Prepare destination path
            String destinationPath = task.getDestinationPath();
            if (destinationPath == null || destinationPath.equals("/")) {
                destinationPath = "root";
            }
            System.out.println("Using destination path: " + destinationPath);

            // Create copy reference
            DriveItem newItem = new DriveItem();
            newItem.name = sourceItem.name;

            ItemReference parentReference = new ItemReference();
            if (destinationPath.equals("root")) {
                parentReference.driveId = "me";
                parentReference.id = "root";
            } else {
                parentReference.id = destinationPath;
            }
            newItem.parentReference = parentReference;

            // Perform copy operation
            System.out.println("Executing copy operation...");
            DriveItem copiedItem;
            if (destinationPath.equals("root")) {
                copiedItem = destinationClient.me()
                    .drive()
                    .root()
                    .children()
                    .buildRequest()
                    .post(newItem);
            } else {
                copiedItem = destinationClient.me()
                    .drive()
                    .items(destinationPath)
                    .children()
                    .buildRequest()
                    .post(newItem);
            }

            System.out.println("File copied successfully. New file ID: " + copiedItem.id);

        } catch (Exception e) {
            String errorMsg = "Failed to copy file in OneDrive: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();
            throw new RuntimeException(errorMsg, e);
        }
    }

    private GraphServiceClient<?> getClientForAccount(CloudDriveConnection connection) {
        try {
            System.out.println("Creating client for account: " + connection.getEmail());
            
            if (connection.getTenantId() == null || connection.getTenantId().trim().isEmpty()) {
                throw new RuntimeException("TenantId is missing for account: " + connection.getEmail());
            }

            ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .clientId(apiConfigService.getOneDriveApiKey())
                .clientSecret(apiConfigService.getOneDriveAppSecret())
                .tenantId(connection.getTenantId())
                .build();

            TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(SCOPES, credential);

            return GraphServiceClient
                .builder()
                .authenticationProvider(authProvider)
                .buildClient();
                
        } catch (Exception e) {
            String errorMsg = "Failed to create OneDrive client for " + connection.getEmail() + ": " + e.getMessage();
            System.err.println(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void deleteFile(String fileId, Long accountId) {
        System.out.println("Starting OneDrive file delete operation...");
        System.out.println("File ID: " + fileId + ", Account ID: " + accountId);

        try {
            Optional<CloudDriveConnection> connection = cloudDriveConnectionRepository.findById(accountId);
            if (!connection.isPresent()) {
                throw new RuntimeException("Account not found. ID: " + accountId);
            }
            
            GraphServiceClient<?> client = getClientForAccount(connection.get());

            System.out.println("Executing delete operation...");
            client.me()
                .drive()
                .items(fileId)
                .buildRequest()
                .delete();
            
            System.out.println("File deleted successfully");
            
        } catch (Exception e) {
            String errorMsg = "OneDrive delete error: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();
            throw new RuntimeException(errorMsg, e);
        }
    }

    public DriveItem getFileMetadata(String fileId, Long accountId) {
        System.out.println("Fetching OneDrive file metadata...");
        System.out.println("File ID: " + fileId + ", Account ID: " + accountId);
        
        try {
            Optional<CloudDriveConnection> connection = cloudDriveConnectionRepository.findById(accountId);
            if (!connection.isPresent()) {
                throw new RuntimeException("Account not found. ID: " + accountId);
            }
            
            GraphServiceClient<?> client = getClientForAccount(connection.get());

            DriveItem metadata = client.me()
                .drive()
                .items(fileId)
                .buildRequest()
                .get();
                
            System.out.println("File metadata retrieved successfully");
            return metadata;
            
        } catch (Exception e) {
            String errorMsg = "Failed to get OneDrive file metadata: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void validateConnection(CloudDriveConnection connection) {
        try {
            GraphServiceClient<?> client = getClientForAccount(connection);
            
            // Try to access root folder to validate connection
            client.me()
                .drive()
                .root()
                .buildRequest()
                .get();
                
            System.out.println("OneDrive connection validated successfully for: " + connection.getEmail());
            
        } catch (Exception e) {
            String errorMsg = "Failed to validate OneDrive connection: " + e.getMessage();
            System.err.println(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }
}