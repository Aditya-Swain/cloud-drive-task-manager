package com.nobx.rocketdrive.batch;

import com.nobx.rocketdrive.entity.Task;
import com.nobx.rocketdrive.enums.TaskStatusEnum;
import com.nobx.rocketdrive.service.impl.CloudOperationService;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskProcessor implements ItemProcessor<Task, Task> {
    
    @Autowired
    private CloudOperationService cloudOperationService;

    @Override
    public Task process(Task task) throws Exception {
        try {
            return cloudOperationService.executeCloudOperation(task);
        } catch (Exception e) {
            task.setStatus(TaskStatusEnum.FAILED);
            return task;
        }
    }
}