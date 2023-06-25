package com.example.demo.batch.config;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimpleTasklet implements Tasklet {
    @Override
    public RepeatStatus execute(StepContribution  stepContribution, ChunkContext chunkContext) throws Exception {
        log.info(">>> parallel stream ");
        
        
        return RepeatStatus.FINISHED;
    }
}