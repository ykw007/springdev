package com.example.demo.batch.config;

import java.nio.file.Path;
import java.nio.file.Paths;

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
        
        
        Path currentPath = Paths.get("");
        String path = currentPath.toAbsolutePath().toString();
        
        log.info("\n\nParallel word count example using Old Testement King James bible : "+path);
        
        SimplParallelStreamWordCounteList aa = new SimplParallelStreamWordCounteList();
        aa.textWordCount("./src/main/resources/input/sample1.txt");
        
        return RepeatStatus.FINISHED;
    }
}