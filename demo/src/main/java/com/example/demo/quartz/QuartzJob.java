package com.example.demo.quartz;

import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobLocator;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
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
   private JobLauncher jobLauncher;
   
   @Override
   protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
      try {
         log.info("executeInternal called ! ");
         
         String jobName = context.getJobDetail().getJobDataMap().getString(JOB_NANE);
         log.info("{} started!", jobName);
         
         //job 내의 파라미터가 모두 동일한 경우 1회만 실행되고 중복 job 으로 분류되어 실행이 불가.
         //currentTime을 파라미터에 추가하여 이를 방지.
         JobParametersBuilder jpb = new JobParametersBuilder();
         jpb.addLong("currTime", System.currentTimeMillis());
         Job job = jobLocator.getJob(jobName);
         
         jobLauncher.run(job, jpb.toJobParameters());  //batch job 실행
         
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
