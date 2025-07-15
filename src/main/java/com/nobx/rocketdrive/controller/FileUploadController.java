package com.nobx.rocketdrive.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.nobx.rocketdrive.service.impl.DropboxServiceImpl;
import com.nobx.rocketdrive.service.impl.GoogleDriveServiceImpl;

import lombok.extern.slf4j.Slf4j;
import java.util.*;

@RestController
@RequestMapping("/api")
@Slf4j
public class FileUploadController {
	@Autowired
	private GoogleDriveServiceImpl googleDriveService;

	@Autowired
	private DropboxServiceImpl dropboxService;

	@PostMapping("/upload-file/{accountId}")
	public ResponseEntity<?> uploadFiles(@PathVariable Long accountId, @RequestParam("files") List<MultipartFile> files,
			@RequestParam("destination_path") String destinationPath,
			@RequestParam("account_type") String accountType) {
		try {
			log.info("Received {} files to upload for account {}", files.size(), accountId);

			if (files.isEmpty()) {
				return ResponseEntity.badRequest().body("No files received");
			}

			Map<String, Object> response = new HashMap<>();
			List<String> uploadedFileIds = new ArrayList<>();

			if ("dropbox".equals(accountType)) {
				List<String> dropboxPaths = dropboxService.uploadFiles(accountId, files, destinationPath);
				response.put("message", "Files uploaded successfully to Dropbox");
				response.put("filePaths", dropboxPaths);
			} else if ("google_drive".equals(accountType)) {
				for (MultipartFile file : files) {
					String fileId = googleDriveService.uploadFile(file, destinationPath, accountId);
					uploadedFileIds.add(fileId);
				}
				response.put("message", "Files uploaded successfully to Google Drive");
				response.put("fileIds", uploadedFileIds);
			} else {
				return ResponseEntity.badRequest().body("Invalid account type");
			}

			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Error uploading files", e);
			return ResponseEntity.internalServerError().body("Error uploading files: " + e.getMessage());
		}
	}

	@PostMapping("/upload-folder/{accountId}")
	public ResponseEntity<?> uploadFolder(@PathVariable Long accountId,
			@RequestParam("folderFiles") List<MultipartFile> files, @RequestParam("folder_name") String folderName,
			@RequestParam("destination_path") String destinationPath,
			@RequestParam("account_type") String accountType) {
		try {
			log.info("Received folder upload request for account {}", accountId);

			if (files.isEmpty()) {
				return ResponseEntity.badRequest().body("No files received");
			}

			Map<String, Object> response = new HashMap<>();

			if ("dropbox".equals(accountType)) {
				String folderPath = dropboxService.createFolder(accountId, folderName, destinationPath);
				List<String> uploadedPaths = dropboxService.uploadFiles(accountId, files, folderPath);
				response.put("message", "Folder uploaded successfully to Dropbox");
				response.put("folderPath", folderPath);
				response.put("filePaths", uploadedPaths);
			} else if ("google_drive".equals(accountType)) {
				String folderId = googleDriveService.createFolder(folderName, destinationPath, accountId);
				List<String> uploadedFileIds = new ArrayList<>();
				for (MultipartFile file : files) {
					String fileId = googleDriveService.uploadFile(file, folderId, accountId);
					uploadedFileIds.add(fileId);
				}
				response.put("message", "Folder uploaded successfully to Google Drive");
				response.put("folderId", folderId);
				response.put("fileIds", uploadedFileIds);
			} else {
				return ResponseEntity.badRequest().body("Invalid account type");
			}

			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Error uploading folder", e);
			return ResponseEntity.internalServerError().body("Error uploading folder: " + e.getMessage());
		}
	}
}
