package com.nobx.rocketdrive.service.impl;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

@Service
@EnableScheduling
public class JobSchedulerService {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    private Job processTaskJob;
    
    private AtomicBoolean isJobRunning = new AtomicBoolean(false);
    
    @Scheduled(fixedRate = 10000)
    public void scheduleTaskProcessing() {
        if (isJobRunning.compareAndSet(false, true)) {
            try {
                JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
                
                JobExecution jobExecution = jobLauncher.run(processTaskJob, jobParameters);
                
                // Wait for job completion with timeout
                long startTime = System.currentTimeMillis();
                while (jobExecution.isRunning()) {
                    if (System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(55)) {
                        // If job is running for too long, break to prevent overlap with next scheduled run
                        break;
                    }
                    Thread.sleep(1000);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isJobRunning.set(false);
            }
        }
    }
}