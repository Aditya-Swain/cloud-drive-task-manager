package com.nobx.rocketdrive.repository;
import com.nobx.rocketdrive.entity.Task;
import com.nobx.rocketdrive.enums.TaskStatusEnum;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findByStatus(TaskStatusEnum status, Pageable pageable);
}