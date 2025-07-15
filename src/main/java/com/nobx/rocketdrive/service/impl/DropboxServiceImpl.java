package com.nobx.rocketdrive.service.impl;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.files.UploadErrorException;
import com.dropbox.core.v2.files.DownloadErrorException;
import com.dropbox.core.v2.files.DeleteErrorException;

import com.nobx.rocketdrive.entity.Task;
import com.nobx.rocketdrive.entity.CloudDriveConnection;
import com.nobx.rocketdrive.repository.CloudDriveConnectionRepository;
import com.nobx.rocketdrive.service.CloudService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DropboxServiceImpl implements CloudService{

	@Autowired
	private CloudDriveConnectionRepository cloudDriveConnectionRepository;

	/**
	 * Copies a file from one Dropbox account to another.
	 *
	 * @param task The task containing file operation details.
	 */
	
	@Override
	public void copyFile(Task task) {
		System.out.println("Starting file copy operation...");
		System.out.println("Source account ID: " + task.getSourceAccountId());
		System.out.println("Destination account ID: " + task.getDestinationAccountId());

		String sourceAccessToken = getAccessTokenByAccountId(task.getSourceAccountId().longValue());
		String destinationAccessToken = getAccessTokenByAccountId(task.getDestinationAccountId().longValue());

		System.out.println("SourceAccessToken :" + sourceAccessToken);
		System.out.println("DestinationAccessToken :" + destinationAccessToken);
		DbxClientV2 sourceClient = getClient(sourceAccessToken);
		DbxClientV2 destinationClient = getClient(destinationAccessToken);

		String sourcePath = task.getSourcePath();
		String destinationPath = task.getDestinationPath();

		try {
			// Download file from source Dropbox
			System.out.println("Attempting to download from source...");
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			var metadata = sourceClient.files().download(sourcePath).download(outputStream);
			System.out.println("Download successful. File size: " + metadata.getSize());
			byte[] fileContent = outputStream.toByteArray();

			// Upload file to destination Dropbox
			String filename = sourcePath.substring(sourcePath.lastIndexOf("/") + 1);
			String destinationFullPath = destinationPath.endsWith("/") ? destinationPath + filename
					: destinationPath + "/" + filename;

			System.out.println("Attempting to upload to destination: " + destinationFullPath);
			var uploadResult = destinationClient.files().uploadBuilder(destinationFullPath)
					.withMode(WriteMode.OVERWRITE).uploadAndFinish(new ByteArrayInputStream(fileContent));

			System.out.println("Upload successful. New file ID: " + uploadResult.getId());
		} catch (DownloadErrorException e) {
			System.err.println("Download error: " + e.getMessage());
			throw new RuntimeException("Dropbox download error: " + e.getMessage(), e);
		} catch (UploadErrorException e) {
			System.err.println("Upload error: " + e.getMessage());
			throw new RuntimeException("Dropbox upload error: " + e.getMessage(), e);
		} catch (DbxException | IOException e) {
			System.err.println("General error: " + e.getMessage());
			throw new RuntimeException("Error during Dropbox operation: " + e.getMessage(), e);
		}
	}

	/**
	 * Deletes a file from Dropbox.
	 *
	 * @param filePath The path of the file to delete.
	 */
	public void deleteFile(String filePath, String sourceAccessToken) {
		// Add more robust logging
		System.err.println("Attempting to delete file: " + filePath);
		System.err.println("Using access token: " + (sourceAccessToken != null ? "Present" : "NULL"));

		if (filePath == null || filePath.isEmpty()) {
			throw new IllegalArgumentException("File path cannot be null or empty");
		}

		DbxClientV2 client = getClient(sourceAccessToken);

		try {
			System.err.println("Executing Dropbox delete operation...");
			var metadata = client.files().deleteV2(filePath);
			System.err.println("File deleted successfully. Metadata: " + metadata.getMetadata().getName());
		} catch (DeleteErrorException e) {
			System.err.println("Dropbox Delete Error: " + e.getMessage());
			e.printStackTrace(); // Print full stack trace
			throw new RuntimeException("Dropbox delete error: " + e.getMessage(), e);
		} catch (DbxException e) {
			System.err.println("Dropbox General Error: " + e.getMessage());
			e.printStackTrace(); // Print full stack trace
			throw new RuntimeException("Error during Dropbox delete operation: " + e.getMessage(), e);
		}
	}

	/**
	 * Uploads multiple files to Dropbox and returns their paths
	 */
	public List<String> uploadFiles(Long accountId, List<MultipartFile> files, String destinationPath) {
		String accessToken = getAccessTokenByAccountId(accountId);
		DbxClientV2 client = getClient(accessToken);
		List<String> uploadedPaths = new ArrayList<>();

		for (MultipartFile file : files) {
			try {
				String fileName = file.getOriginalFilename();
				String fullPath = buildPath(destinationPath, fileName);

				log.info("Uploading file {} to Dropbox path: {}", fileName, fullPath);

				// Upload file to Dropbox
				try (InputStream in = file.getInputStream()) {
					var uploadResult = client.files().uploadBuilder(fullPath).withMode(WriteMode.OVERWRITE)
							.uploadAndFinish(in);

					uploadedPaths.add(uploadResult.getPathDisplay());
					log.info("Successfully uploaded file: {}", uploadResult.getPathDisplay());
				}
			} catch (UploadErrorException e) {
				log.error("Dropbox upload error for file {}: {}", file.getOriginalFilename(), e.getMessage());
				throw new RuntimeException("Dropbox upload error: " + e.getMessage(), e);
			} catch (DbxException | IOException e) {
				log.error("Error during Dropbox operation for file {}: {}", file.getOriginalFilename(), e.getMessage());
				throw new RuntimeException("Error during Dropbox operation: " + e.getMessage(), e);
			}
		}

		return uploadedPaths;
	}

	/**
	 * Creates a folder in Dropbox and returns its path
	 */
	public String createFolder(Long accountId, String folderName, String parentPath) {
		String accessToken = getAccessTokenByAccountId(accountId);
		DbxClientV2 client = getClient(accessToken);
		String folderPath = buildPath(parentPath, folderName);

		try {
			log.info("Creating folder in Dropbox: {}", folderPath);
			var folder = client.files().createFolderV2(folderPath);
			log.info("Successfully created folder: {}", folder.getMetadata().getPathDisplay());
			return folder.getMetadata().getPathDisplay();
		} catch (DbxException e) {
			log.error("Error creating folder in Dropbox: {}", e.getMessage());
			throw new RuntimeException("Error creating folder in Dropbox: " + e.getMessage(), e);
		}
	}

	/**
	 * Uploads files to a specific folder in Dropbox
	 */
	public List<String> uploadFolder(Long accountId, List<MultipartFile> files, String folderName,
			String destinationPath) {
		String folderPath = createFolder(accountId, folderName, destinationPath);
		return uploadFiles(accountId, files, folderPath);
	}

	/**
	 * Helper method to build proper Dropbox paths
	 */
	private String buildPath(String basePath, String name) {
		if (basePath == null || basePath.isEmpty() || basePath.equals("/")) {
			return "/" + name;
		}
		String normalizedBasePath = basePath.startsWith("/") ? basePath : "/" + basePath;
		return normalizedBasePath + (normalizedBasePath.endsWith("/") ? "" : "/") + name;
	}

	/**
	 * Initializes the Dropbox client using an access token.
	 *
	 * @param accessToken The access token for the Dropbox account.
	 * @return The initialized Dropbox client.
	 */
	private DbxClientV2 getClient(String accessToken) {
		DbxRequestConfig config = DbxRequestConfig.newBuilder("rocketdrive-app").build();
		return new DbxClientV2(config, accessToken);
	}

	/**
	 * Fetches the access token for a given account ID.
	 *
	 * @param accountId The account ID.
	 * @return The access token.
	 */
	private String getAccessTokenByAccountId(Long accountId) {
		return cloudDriveConnectionRepository.findById(accountId).map(CloudDriveConnection::getAccessToken)
				.orElseThrow(() -> new RuntimeException("Account not found for ID: " + accountId));
	}

	/*
	 * Helper method to delete a file by account id from another package , since
	 * getAccessTokenByAccountId() method's visibility is private
	 * 
	 */
	@Override
	public void deleteFileWithAccountId(String filePath, Long accountId) {
		String accessToken = getAccessTokenByAccountId(accountId);
		deleteFile(filePath, accessToken);
	}

}
