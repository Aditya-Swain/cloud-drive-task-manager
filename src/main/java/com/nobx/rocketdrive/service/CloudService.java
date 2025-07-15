package com.nobx.rocketdrive.service;

import com.nobx.rocketdrive.entity.Task;

public interface CloudService {
	
	
	 void copyFile(Task task);

	 void deleteFileWithAccountId(String filePath, Long accountId);
	 

}
