package com.nobx.rocketdrive.service.impl;

import com.nobx.rocketdrive.entity.Task;
import com.nobx.rocketdrive.enums.TaskStatusEnum;
import com.nobx.rocketdrive.service.CloudService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class CloudOperationService {

	@Autowired
	private DropboxServiceImpl dropboxService;

	@Autowired
	private GoogleDriveServiceImpl googleDriveService;

	@Autowired
	private OneDriveService oneDriveService;

	public Task executeCloudOperation(Task task) {
		try {
			task.setStatus(TaskStatusEnum.IN_PROGRESS);
			task.setUpdatedAt(LocalDateTime.now());

			// Execute cloud operation based on cloudType and cloudService
			switch (task.getCloudType()) {
			case COPY:
				handleCopyOperation(task);
				break;
			case CUT:
				handleMoveOperation(task);
				break;
			case DELETE:
				handleDeleteOperation(task);
				break;
			default:
				throw new IllegalArgumentException("Unsupported operation type: " + task.getCloudType());
			}

			task.setStatus(TaskStatusEnum.COMPLETED);
		} catch (Exception e) {
			task.setStatus(TaskStatusEnum.FAILED);
		}

		task.setUpdatedAt(LocalDateTime.now());
		return task;
	}

	private void handleCopyOperation(Task task) {
		switch (task.getCloudService()) {
		case GOOGLE_DRIVE:
			// Google Drive copy logic
			System.out.println("google drive copy method() executed.");
			googleDriveService.copyFile(task);
			break;
		case DROPBOX:
			// Dropbox copy logic
			dropboxService.copyFile(task);
			break;
		case ONEDRIVE:
			// OneDrive copy logic
			oneDriveService.copyFile(task);
			break;
		default:
			throw new IllegalArgumentException("Unsupported cloud service: " + task.getCloudService());
		}
	}

	private void handleMoveOperation(Task task) {
		handleCopyOperation(task);
		handleDeleteOperation(task);
	}

	private void handleDeleteOperation(Task task) {
		switch (task.getCloudService()) {
		case GOOGLE_DRIVE:
			// Implementation Google Drive delete logic
			googleDriveService.deleteFileWithAccountId(task.getSourcePath(), task.getSourceAccountId().longValue());
			break;
		case DROPBOX:
			// Implemention Dropbox delete logic

			dropboxService.deleteFileWithAccountId(task.getSourcePath(), task.getSourceAccountId().longValue());
			break;
		case ONEDRIVE:
			// Implemention OneDrive delete logic
			oneDriveService.deleteFile(task.getSourcePath(), task.getSourceAccountId().longValue());

			break;
		default:
			throw new IllegalArgumentException("Unsupported cloud service: " + task.getCloudService());
		}
	}
}