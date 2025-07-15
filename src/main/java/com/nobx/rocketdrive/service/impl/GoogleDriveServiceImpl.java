package com.nobx.rocketdrive.service.impl;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nobx.rocketdrive.entity.CloudDriveConnection;
import com.nobx.rocketdrive.entity.Task;
import com.nobx.rocketdrive.repository.CloudDriveConnectionRepository;
import com.nobx.rocketdrive.service.CloudService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
@Slf4j
public class GoogleDriveServiceImpl implements CloudService{

	@Autowired
	private ApiConfigService apiConfigService;

	@Autowired
	private CloudDriveConnectionRepository cloudDriveConnectionRepository;

	private static final String APPLICATION_NAME = "RocketDrive";
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

	/**
	 * Copies a file from one Google Drive account to another using Google Drive's
	 * native copy method.
	 *
	 * @param task The task containing file operation details.
	 */
	
	@Override
	public void copyFile(Task task) {
		System.out.println("Starting Google Drive file copy operation...");
		System.out.println("Task details: " + task);

		String sourceAccessToken = getAccessTokenByAccountId(task.getSourceAccountId().longValue());
		String destinationAccessToken = getAccessTokenByAccountId(task.getDestinationAccountId().longValue());

		Drive sourceClient = getClient(sourceAccessToken, task.getSourceAccountId().longValue());
		Drive destinationClient = getClient(destinationAccessToken, task.getDestinationAccountId().longValue());

		String sourceFileId = task.getSourcePath();
		String destinationFolderId = task.getDestinationPath();

		try {
			File sourceFile = sourceClient.files().get(sourceFileId).setFields("name").setSupportsAllDrives(true)
					.execute();
			String fileName = sourceFile.getName();
			System.out.println("Source file name: " + fileName);

			if (task.getDestinationEmail() == null || task.getDestinationEmail().isEmpty()) {
				throw new IllegalArgumentException("Destination email is required for file sharing.");
			}

			Permission permission = new Permission().setType("user").setRole("writer")
					.setEmailAddress(task.getDestinationEmail());

			sourceClient.permissions().create(sourceFileId, permission).setFields("id").setSupportsAllDrives(true)
					.execute();
			System.out.println("Permission granted to destination email: " + task.getDestinationEmail());

			if (destinationFolderId == null || destinationFolderId.equals("/")) {
				destinationFolderId = "root";
			}

			File fileMetadata = new File();
			fileMetadata.setName(fileName);
			fileMetadata.setParents(Collections.singletonList(destinationFolderId));

			File copiedFile = destinationClient.files().copy(sourceFileId, fileMetadata).setFields("id, name, parents")
					.execute();

			System.out.println("File copied successfully: " + copiedFile);

		} catch (GoogleJsonResponseException e) {
			System.err.println("Google Drive API error: " + e.getDetails().getMessage());
			throw new RuntimeException("Google Drive API error: " + e.getDetails().getMessage(), e);
		} catch (IOException e) {
			System.err.println("Error during Google Drive operation: " + e.getMessage());
			throw new RuntimeException("Error during Google Drive operation: " + e.getMessage(), e);
		}
	}

	
	public void deleteFile(String fileId, String accessToken, Long accountId) {
		System.out.println("Starting Google Drive file delete operation...");
		System.out.println("File ID: " + fileId);

		Drive client = getClient(accessToken, accountId);

		try {
			client.files().delete(fileId).execute();
			System.out.println("File deleted successfully");
		} catch (GoogleJsonResponseException e) {
			System.err.println("Delete error: " + e.getDetails().getMessage());
			throw new RuntimeException("Google Drive delete error: " + e.getDetails().getMessage(), e);
		} catch (IOException e) {
			System.err.println("Error during delete operation: " + e.getMessage());
			throw new RuntimeException("Error during Google Drive delete operation: " + e.getMessage(), e);
		}
	}

	private Drive getClient(String accessToken, Long accountId) {
		return getClient(accessToken, accountId, 0);
	}

	private Drive getClient(String accessToken, Long accountId, int retryCount) {
		try {
			final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

			Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
					.setAccessToken(accessToken);

			return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME)
					.build();

		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 401 && retryCount == 0) {
				System.out.println("Unauthorized error. Refreshing token...");
				CloudDriveConnection connection = cloudDriveConnectionRepository.findById(accountId)
						.orElseThrow(() -> new RuntimeException("Account not found for ID: " + accountId));
				String refreshedToken = refreshAccessToken(connection);
				return getClient(refreshedToken, accountId, retryCount + 1);
			}
			throw new RuntimeException("Google Drive API error: " + e.getDetails().getMessage(), e);
		} catch (GeneralSecurityException | IOException e) {
			throw new RuntimeException("Failed to initialize Google Drive client: " + e.getMessage(), e);
		}
	}

	private String getAccessTokenByAccountId(Long accountId) {
		CloudDriveConnection connection = cloudDriveConnectionRepository.findById(accountId)
				.orElseThrow(() -> new RuntimeException("Account not found for ID: " + accountId));

		if (connection.getExpiryTime() != null && System.currentTimeMillis() > connection.getExpiryTime()) {
			System.out.println("Access token expired, refreshing...");
			return refreshAccessToken(connection);
		}

		return connection.getAccessToken();
	}

	private String refreshAccessToken(CloudDriveConnection connection) {
		try {
			String tokenEndpoint = "https://oauth2.googleapis.com/token";

			URL url = new URL(tokenEndpoint);
			HttpURLConnection connectionRequest = (HttpURLConnection) url.openConnection();
			connectionRequest.setRequestMethod("POST");
			connectionRequest.setDoOutput(true);
			connectionRequest.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			String requestBody = "client_id=" + apiConfigService.getGoogleDriveApiKey() + "&client_secret="
					+ apiConfigService.getGoogleDriveAppSecret() + "&refresh_token=" + connection.getRefreshToken()
					+ "&grant_type=refresh_token";

			try (OutputStream os = connectionRequest.getOutputStream()) {
				os.write(requestBody.getBytes());
				os.flush();
			}

			if (connectionRequest.getResponseCode() == 200) {
				try (InputStream is = connectionRequest.getInputStream()) {
					String response = new BufferedReader(new InputStreamReader(is)).lines().reduce("", String::concat);
					JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

					String newAccessToken = jsonResponse.get("access_token").getAsString();
					long expiresIn = jsonResponse.get("expires_in").getAsLong();

					connection.setAccessToken(newAccessToken);
					connection.setExpiryTime(System.currentTimeMillis() + (expiresIn * 1000));
					cloudDriveConnectionRepository.save(connection);

					return newAccessToken;
				}
			} else {
				throw new RuntimeException("Failed to refresh token: HTTP " + connectionRequest.getResponseCode());
			}

		} catch (IOException e) {
			throw new RuntimeException("Error refreshing access token: " + e.getMessage(), e);
		}
	}

	@Override
	public void deleteFileWithAccountId(String filePath, Long accountId) {
		String accessToken = getAccessTokenByAccountId(accountId);
		deleteFile(filePath, accessToken, accountId);
	}

	public String uploadFile(MultipartFile file, String destinationPath, Long accountId) {
		log.info("Starting file upload to Google Drive for account: {}", accountId);
		String accessToken = getAccessTokenByAccountId(accountId);
		Drive driveService = getClient(accessToken, accountId);

		try {
			// Create file metadata
			File fileMetadata = new File();
			fileMetadata.setName(file.getOriginalFilename());

			// Set parent folder if destination path is provided
			if (destinationPath != null && !destinationPath.equals("/")) {
				fileMetadata.setParents(Collections.singletonList(destinationPath));
			}

			// Prepare file content
			InputStreamContent mediaContent = new InputStreamContent(file.getContentType(), file.getInputStream());

			// Upload file
			File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
					.setFields("id, name, webViewLink").setSupportsAllDrives(true).execute();

			log.info("File uploaded successfully. File ID: {}", uploadedFile.getId());
			return uploadedFile.getId();

		} catch (Exception e) {
			log.error("Error uploading file to Google Drive", e);
			throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
		}
	}

	public String createFolder(String folderName, String parentPath, Long accountId) {
		log.info("Creating folder '{}' for account: {}", folderName, accountId);
		String accessToken = getAccessTokenByAccountId(accountId);
		Drive driveService = getClient(accessToken, accountId);

		try {
			File folderMetadata = new File();
			folderMetadata.setName(folderName);
			folderMetadata.setMimeType("application/vnd.google-apps.folder");

			// Set parent folder if parent path is provided
			if (parentPath != null && !parentPath.equals("/")) {
				folderMetadata.setParents(Collections.singletonList(parentPath));
			}

			File folder = driveService.files().create(folderMetadata).setFields("id, name").setSupportsAllDrives(true)
					.execute();

			log.info("Folder created successfully. Folder ID: {}", folder.getId());
			return folder.getId();

		} catch (Exception e) {
			log.error("Error creating folder in Google Drive", e);
			throw new RuntimeException("Failed to create folder: " + e.getMessage(), e);
		}
	}
}
