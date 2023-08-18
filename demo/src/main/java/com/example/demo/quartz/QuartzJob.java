package com.example.demo.quartz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.configuration.JobLocator;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.example.demo.quartz.utils.BeanUtils;
import lombok.extern.slf4j.Slf4j;


@Slf4j
//clustering 모드에선 아래 어노테이션이 동작하지 않음
//@DisallowConcurrentExecution
public class QuartzJob extends QuartzJobBean implements InterruptableJob {

   private static final String JOB_NANE = "JOB_NAME";
   
   private volatile boolean isJobInterrupted = false; 
   private volatile Thread currThread;
   
   @Autowired
   private JobLocator jobLocator;
   
   @Autowired
   private JobRepository jobRepository;
   
   @Autowired
   private JobLauncher jobLauncher;
;
   
   @Override
   protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
      try {
         log.info("executeInternal called ! ");
         
         String jobName = context.getJobDetail().getJobDataMap().getString(JOB_NANE);
         log.info("{} started!", jobName);
         
         TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
         jobLauncher.setJobRepository(jobRepository);
         jobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor());
         
         Files.walk(Paths.get("./src/main/resources/input/")) // 경로 스트림 생성
			//.collect(Collectors.toList()) // 더 나은 병렬화를 위해 경로를 목록으로 수집
			//.parallelStream() // 여러 스레드에서 이 스트림 처리
			.filter(Files::isRegularFile) // 파일이 아닌 디렉토리 필터링
			.map(Path::toFile) // 경로를 파일 객체로 변환
			//.parallel()
			.sorted((a, b) -> Long.compare(a.lastModified(), b.lastModified())) // 정렬 파일 날짜
			.limit(100) // 처리 파일로 제한
			//.forEach(f -> { // 병렬 스트림인 경우 순서가 보장되지 않음
			.forEachOrdered(f -> { // 병렬 스트림인 경우에도 순서가 보장됨
				//process(f);
				
				try {
					
					log.info("filename : {} started!", f.getName());
			         JobParametersBuilder jpb = new JobParametersBuilder();
			         jpb.addLong("currTime", System.nanoTime());
			         jpb.addString("filename", f.getName());
			         Job job = jobLocator.getJob(jobName);
					
					jobLauncher.run(job, jpb.toJobParameters());
				} catch (NoSuchJobException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JobExecutionAlreadyRunningException | JobRestartException | JobInstanceAlreadyCompleteException
						| JobParametersInvalidException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
         
         /*
         //job 내의 파라미터가 모두 동일한 경우 1회만 실행되고 중복 job 으로 분류되어 실행이 불가.
         //currentTime을 파라미터에 추가하여 이를 방지.
         JobParametersBuilder jpb = new JobParametersBuilder();
         jpb.addLong("currTime", System.nanoTime());
         Job job = jobLocator.getJob(jobName);
         

         
         jobLauncher.run(job, jpb.toJobParameters());  //batch job 실행
         */
         /*
         //ForkJoinPool customThreadPool = new ForkJoinPool(5);
         
         //customThreadPool.submit(()->
         {

 			try {
				Files.walk(Paths.get("./src/main/resources/input/")) // 경로 스트림 생성
				//.collect(Collectors.toList()) // 더 나은 병렬화를 위해 경로를 목록으로 수집
				//.parallelStream() // 여러 스레드에서 이 스트림 처리
				.filter(Files::isRegularFile) // 파일이 아닌 디렉토리 필터링
				.map(Path::toFile) // 경로를 파일 객체로 변환
				.parallel()
				.sorted((a, b) -> Long.compare(a.lastModified(), b.lastModified())) // 정렬 파일 날짜
				.limit(100) // 처리 파일로 제한
				//.forEach(f -> { // 병렬 스트림인 경우 순서가 보장되지 않음
				.forEachOrdered(f -> { // 병렬 스트림인 경우에도 순서가 보장됨
					//process(f);
					
					try {
						
						log.info("filename : {} started!", f.getName());
				         JobParametersBuilder jpb = new JobParametersBuilder();
				         jpb.addLong("currTime", System.nanoTime());
				         jpb.addString("filename", f.getName());
				         Job job = jobLocator.getJob(jobName);
						
						jobLauncher.run(job, jpb.toJobParameters());
					} catch (NoSuchJobException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (JobExecutionAlreadyRunningException | JobRestartException | JobInstanceAlreadyCompleteException
							| JobParametersInvalidException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
 			

	             

         
         }//);

         //customThreadPool.shutdown();
        	    */    
         
      } catch (Exception e) {
         log.error("ex in job execute: {}", e.getMessage());
      }
   }

   @Override
   public void interrupt() throws UnableToInterruptJobException {
      isJobInterrupted = true;
      if(currThread != null) {
         log.info("interrupting-{"+currThread.getName()+"}");
         currThread.interrupt();
      }
   }
   
}
