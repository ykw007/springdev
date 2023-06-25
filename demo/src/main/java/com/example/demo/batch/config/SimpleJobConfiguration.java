package com.example.demo.batch.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.JobRegistry;

import org.springframework.batch.core.configuration.support.JobRegistryBeanPostProcessor;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SimpleJobConfiguration {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
	@Bean
	public JobRegistryBeanPostProcessor jobRegistryBeanPostProcessor(JobRegistry jobRegistry) {
		JobRegistryBeanPostProcessor jobRegistryBeanPostProcessor = new JobRegistryBeanPostProcessor();
		jobRegistryBeanPostProcessor.setJobRegistry(jobRegistry);
		return jobRegistryBeanPostProcessor;
	}
	
    @Bean(name = "simpleJob")
    public Job job(JobRepository jobRepository, PlatformTransactionManager transactionManager, Tasklet tasklet) {
        return new JobBuilder("simpleJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(step(jobRepository, transactionManager, tasklet))
                .build();
    }

    
	@Bean
	@Qualifier("step")
    public Step step(JobRepository jobRepository, PlatformTransactionManager transactionManager, Tasklet tasklet1) {
        log.info("Building step");
        return new StepBuilder("myTasklet", jobRepository)
                .tasklet(tasklet1, transactionManager).allowStartIfComplete(true)
                .build();

    }

    @Bean
    public SimpleTasklet simpleTasklet() {
        log.info("Building tasklet");
        var tasklet = new SimpleTasklet(jdbcTemplate);
        return tasklet;
    }
}