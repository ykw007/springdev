package com.example.demo.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

	private static final Logger log = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

	private final JdbcTemplate jdbcTemplate;
	
	@Autowired	
	private ThreadPoolTaskExecutor taskExecutor;	

	@Autowired
	public JobCompletionNotificationListener(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		if(jobExecution.getStatus() == BatchStatus.COMPLETED) {
			log.info("!!! JOB FINISHED! Time to verify the results jobid : "+jobExecution.getJobId());

			//jdbcTemplate.query("SELECT first_name, last_name FROM people",
			//	(rs, row) -> new Person(
			//		rs.getString(1),
			//		rs.getString(2))
			//).forEach(person -> log.info("Found <" + person + "> in the database."));
		}else {
			log.info("!!! JOB FAILED!!! jobid : "+jobExecution.getJobId());
		}
		//taskExecutor.shutdown();
	}
	
}
