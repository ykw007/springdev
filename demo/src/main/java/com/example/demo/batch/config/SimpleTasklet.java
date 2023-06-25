package com.example.demo.batch.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimpleTasklet implements Tasklet {
	
	private final JdbcTemplate jdbcTemplate;

	@Autowired
	public SimpleTasklet(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
    @Override
    public RepeatStatus execute(StepContribution  stepContribution, ChunkContext chunkContext) throws Exception {
        log.info(">>> parallel stream test");
        
        
        Path currentPath = Paths.get("");
        String path = currentPath.toAbsolutePath().toString();
        
        log.info("file path : "+path);
        
        //SimplParallelStreamWordCounteList aa = new SimplParallelStreamWordCounteList();
        //aa.textWordCount("./src/main/resources/input/sample1.txt");
        Integer count = jdbcTemplate.queryForObject(
        		"select count(*) from people", Integer.class);
        log.info(count.toString());

        Files.walk(Paths.get("./src/main/resources/input/")) // create a stream of paths
        .collect(Collectors.toList()) // collect paths into list to better parallize
        .parallelStream() // process this stream in multiple threads
        .filter(Files::isRegularFile) // filter out any non-files (such as directories)
        .map(Path::toFile) // convert Path to File object
        .sorted((a, b) -> Long.compare(a.lastModified(), b.lastModified())) // sort files date
        .limit(500) // limit processing to 500 files (optional)
        .forEach(f -> { // 병렬 스트림인 경우 순서가 보장되지 않음
        //.forEachOrdered(f -> { // 병렬 스트림인 경우에도 순서가 보장됨

            log.info(f.getName()+ " " + Thread.currentThread().getName());
            
            Integer count2 = jdbcTemplate.queryForObject(
            		"select count(*) from people", Integer.class);
            log.info(">> forEachOrdered 처리시 DB 테스트  :"+count2.toString());
        });
        
        return RepeatStatus.FINISHED;
    }
}