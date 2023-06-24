package com.example.demo.quartz;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QuartzJobListener implements JobListener {
    @Override
    public String getName() {
        return this.getClass().getName();
    }


    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        log.info("Job 수행 되기 전");
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        log.info("Job 중단");
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        log.info("Job 수행 완료 후");
    }
}