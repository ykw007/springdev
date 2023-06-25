package com.example.demo.quartz;

import java.util.Map;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class QuartzService {
    private final Scheduler scheduler;
    public static final String JOB_NANE = "JOB_NAME";

    @PostConstruct
    public void init() {
        try {
            scheduler.clear();
            scheduler.getListenerManager().addJobListener(new QuartzJobListener());
            scheduler.getListenerManager().addTriggerListener(new QuartzTriggerListener());

            addJob(QuartzJob.class, "SampleJob", "createJob1 입니다", null , "0/30 * * * * ?");
            addJob(QuartzJob.class, "exampleJob", "exampleJob 입니다", null , "0/30 * * * * ?");


        } catch (Exception e){
            log.error("addJob error  : {}", e);

        }
    }

    //Job 추가
    public <T extends Job> void addJob(Class<? extends Job> job ,String name, String  dsec, Map paramsMap, String cron) throws SchedulerException {
        JobDetail jobDetail = buildJobDetail(job,name,dsec,paramsMap);
        Trigger trigger = buildCronTrigger(cron);
        if(scheduler.checkExists(jobDetail.getKey())) scheduler.deleteJob(jobDetail.getKey());
        scheduler.scheduleJob(jobDetail,trigger);
    }


    //JobDetail 생성
    public <T extends Job> JobDetail buildJobDetail(Class<? extends Job> job, String name, String desc, Map paramsMap) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(JOB_NANE, name);
        jobDataMap.put("executeCount", 1);


        return JobBuilder
                .newJob(job)
                .withIdentity(name)
                .withDescription(desc)
                .usingJobData(jobDataMap)
                .build();
    }

    //Trigger 생성
    private Trigger buildCronTrigger(String cronExp) {
        return TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExp))
                .build();
    }
   }