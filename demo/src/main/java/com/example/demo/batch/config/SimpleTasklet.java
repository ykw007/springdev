package com.example.demo.batch.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimpleTasklet implements Tasklet {
	
	private final JdbcTemplate jdbcTemplate;
	
	private int aa = 0;

	@Autowired
	public SimpleTasklet(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
    @Override
    public RepeatStatus execute(StepContribution  stepContribution, ChunkContext chunkContext) throws Exception {
        //log.info(">>> parallel stream test");
        
        StepContext stepContext = chunkContext.getStepContext();
        StepExecution stepExecution = stepContext.getStepExecution();
        JobExecution jobExecution = stepExecution.getJobExecution();
        long jobInstanceId = jobExecution.getJobId();
        
        String filename = (String) chunkContext.getStepContext()
                .getJobParameters()
                .get("filename");

        Long jobId = jobExecution.getJobId();
        
        log.info(">>> filename : "+filename);
        //log.info(">>> jobId : "+jobId);
        //log.info(">>> jobInstanceId : "+jobInstanceId);
        //log.info(">>> stepExecution Id : "+stepExecution.getId());
        //log.info(">>> stepContext Id : "+stepContext.getId());
        
        Path currentPath = Paths.get("");
        String path = currentPath.toAbsolutePath().toString();
        
        //log.info("file path : "+path);
        
        //SimplParallelStreamWordCounteList aa = new SimplParallelStreamWordCounteList();
        //aa.textWordCount("./src/main/resources/input/sample1.txt");
        //Integer count = jdbcTemplate.queryForObject(
        //		"select count(*) from people", Integer.class);
        //log.info(count.toString());

        ForkJoinPool customThreadPool = new ForkJoinPool(4);
        
        customThreadPool.submit(()->
        {
			/*
			Files.walk(Paths.get("./src/main/resources/input/")) // 경로 스트림 생성
			.collect(Collectors.toList()) // 더 나은 병렬화를 위해 경로를 목록으로 수집
			.parallelStream() // 여러 스레드에서 이 스트림 처리
			.filter(Files::isRegularFile) // 파일이 아닌 디렉토리 필터링
			.map(Path::toFile) // 경로를 파일 객체로 변환
			.parallel()
			.sorted((a, b) -> Long.compare(a.lastModified(), b.lastModified())) // 정렬 파일 날짜
			.limit(100) // 처리 파일로 제한
			.forEach(f -> { // 병렬 스트림인 경우 순서가 보장되지 않음
			//.forEachOrdered(f -> { // 병렬 스트림인 경우에도 순서가 보장됨
				process(f);

			});
			*/
			for(int i = 0;i < 20;i++) {
				log.info(">>>>>>>>>>> " + i+ " " + Thread.currentThread().getName());
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
        );
        

        customThreadPool.shutdown();
       
        
        if(filename.equals("test3.txt"))
        {
        	aa = 0;
        }
        
        //log.info(">>> filename : "+filename+":: aa : "+aa);
        
		for(int i = 0;i < 20;i++) {
	        //log.info(">>> filename : "+filename+":: for aa : "+aa);
			//log.info(">>>>>>>>>>> out " + (aa++));

	       // log.info(">>> jobId : "+jobId);
	        log.info(">>> jobInstanceId : "+jobInstanceId);
	        log.info(">>> stepExecution Id : "+stepExecution.getId());
	        //log.info(">>> stepContext Id : "+stepContext.getId());
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
        
        return RepeatStatus.FINISHED;
    }
    
    private void process(File f) {
    	
	    log.info(f.getName()+ " " + Thread.currentThread().getName());
	    
	    Integer count2 = jdbcTemplate.queryForObject(
	    		"select count(*) from people", Integer.class);
	    log.info(">> 처리시 DB 테스트  :"+count2.toString());

    }
}