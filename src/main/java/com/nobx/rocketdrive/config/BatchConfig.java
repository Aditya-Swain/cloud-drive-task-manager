package com.nobx.rocketdrive.config;

import com.nobx.rocketdrive.entity.Task;
import com.nobx.rocketdrive.enums.TaskStatusEnum;
import com.nobx.rocketdrive.repository.TaskRepository;

import jakarta.annotation.PreDestroy;

import com.nobx.rocketdrive.batch.TaskProcessor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;


@Configuration
public class BatchConfig {
    
    @Autowired
    private TaskRepository taskRepository;
    
    private ExecutorService executorService;
    
    @Bean
    public ExecutorService executorService() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2,                         // core pool size
            6,                         // max pool size 
            60L, TimeUnit.SECONDS,      // thread keep-alive time
            new LinkedBlockingQueue<>(10), // queue capacity
            new ThreadFactory() {
                private int threadCount = 1;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("TaskThread-" + threadCount++);
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // Set the maximum number of concurrent executions
        executor.setMaximumPoolSize(10);
        
        this.executorService = executor;
        return executor;
    }
    
    @Bean
    public TaskExecutor taskExecutor() {
        ConcurrentTaskExecutor taskExecutor = new ConcurrentTaskExecutor(executorService());
        return taskExecutor;
    }
    
    @Bean
    public RepositoryItemReader<Task> reader() {
        RepositoryItemReader<Task> reader = new RepositoryItemReader<>();
        reader.setRepository(taskRepository);
        reader.setMethodName("findByStatus");
        reader.setArguments(java.util.Arrays.asList(TaskStatusEnum.PENDING));
        
        Map<String, Sort.Direction> sorts = new HashMap<>();
        sorts.put("id", Sort.Direction.ASC);
        reader.setSort(sorts);
        
        reader.setPageSize(20);
        return reader;
    }
    
    @Bean
    public RepositoryItemWriter<Task> writer() {
        RepositoryItemWriter<Task> writer = new RepositoryItemWriter<>();
        writer.setRepository(taskRepository);
        writer.setMethodName("save");
        return writer;
    }
    
    @Bean
    public Job processTaskJob(JobRepository jobRepository, Step taskStep) {
        return new JobBuilder("processTaskJob", jobRepository)
            .preventRestart()
            .start(taskStep)
            .build();
    }
    
    @Bean
    public Step taskStep(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        TaskProcessor processor) {
        return new StepBuilder("taskStep", jobRepository)
            .<Task, Task>chunk(20, transactionManager)
            .reader(reader())
            .processor(processor)
            .writer(writer())
            .taskExecutor(taskExecutor())
            .build();
    }
    
    @PreDestroy
    public void shutdownExecutorService() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }
}