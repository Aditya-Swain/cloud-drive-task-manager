package com.nobx.rocketdrive.service;

import com.nobx.rocketdrive.entity.Task;

public interface DropboxService {
	
	void copyFile(Task task);
	
	void deleteFile(String filePath, String sourceAccessToken);

}
