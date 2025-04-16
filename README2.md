package com.example.demo.quartz.api;

import com.example.demo.quartz.QuartzService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Quartz 스케줄러를 REST API를 통해 제어할 수 있도록 제공하는 컨트롤러 클래스.
 * 지원 기능: Job 시작, 중지, 일시정지, 재시작, cron 수정.
 */
@RestController
@RequestMapping("/api/quartz")
@RequiredArgsConstructor
@Slf4j
public class QuartzController {

    private final QuartzService quartzService;

    /**
     * 지정한 Job 이름으로 스케줄러 Job 시작
     * @param jobName Job 이름
     * @param cron 실행 주기를 나타내는 cron 표현식
     * @return 성공 메시지
     */
    @PostMapping("/start")
    public ResponseEntity<String> startJob(@RequestParam String jobName, @RequestParam(defaultValue = "0/30 * * * * ?") String cron) {
        try {
            quartzService.startJob(jobName, cron);
            return ResponseEntity.ok("Job started with cron: " + cron);
        } catch (Exception e) {
            log.error("Error starting job", e);
            return ResponseEntity.internalServerError().body("Failed to start job: " + e.getMessage());
        }
    }

    /**
     * 지정한 Job 중지 (삭제)
     * @param jobName 중지할 Job 이름
     */
    @PostMapping("/stop")
    public ResponseEntity<String> stopJob(@RequestParam String jobName) {
        try {
            quartzService.stopJob(jobName);
            return ResponseEntity.ok("Job stopped: " + jobName);
        } catch (Exception e) {
            log.error("Error stopping job", e);
            return ResponseEntity.internalServerError().body("Failed to stop job: " + e.getMessage());
        }
    }

    /**
     * Job 일시정지
     * @param jobName 일시정지할 Job 이름
     */
    @PostMapping("/pause")
    public ResponseEntity<String> pauseJob(@RequestParam String jobName) {
        try {
            quartzService.pauseJob(jobName);
            return ResponseEntity.ok("Job paused: " + jobName);
        } catch (Exception e) {
            log.error("Error pausing job", e);
            return ResponseEntity.internalServerError().body("Failed to pause job: " + e.getMessage());
        }
    }

    /**
     * Job 재시작 (일시정지된 Job 재개)
     * @param jobName 재시작할 Job 이름
     */
    @PostMapping("/resume")
    public ResponseEntity<String> resumeJob(@RequestParam String jobName) {
        try {
            quartzService.resumeJob(jobName);
            return ResponseEntity.ok("Job resumed: " + jobName);
        } catch (Exception e) {
            log.error("Error resuming job", e);
            return ResponseEntity.internalServerError().body("Failed to resume job: " + e.getMessage());
        }
    }

    /**
     * 실행 중인 Job의 Cron 표현식을 수정함
     * @param jobName 대상 Job 이름
     * @param newCron 수정할 새로운 Cron 표현식
     */
    @PostMapping("/update-cron")
    public ResponseEntity<String> updateCron(@RequestParam String jobName, @RequestParam String newCron) {
        try {
            quartzService.updateJobCron(jobName, newCron);
            return ResponseEntity.ok("Cron updated to: " + newCron);
        } catch (Exception e) {
            log.error("Error updating cron", e);
            return ResponseEntity.internalServerError().body("Failed to update cron: " + e.getMessage());
        }
    }
} 


//----------------------------

package com.example.demo.quartz;

import java.util.Map;

import org.quartz.*;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * QuartzService
 * Quartz Scheduler를 제어하는 서비스 클래스입니다.
 * - Job 등록, 시작, 중지, 일시정지, 재시작, cron 표현식 변경 등을 제공합니다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class QuartzService {

    // Quartz 스케줄러 객체
    private final Scheduler scheduler;

    // JobDataMap 키로 사용할 상수
    public static final String JOB_NAME = "JOB_NAME";

    /**
     * Job 시작 (존재 시 삭제 후 재등록)
     * @param jobName 실행할 Job 이름 (JobKey, TriggerKey에 사용됨)
     * @param cron Cron 표현식 (예: "0/30 * * * * ?")
     */
    public void startJob(String jobName, String cron) throws Exception {
        JobKey jobKey = JobKey.jobKey(jobName);
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey); // 기존 Job 제거 후 재등록
        }

        // Job 정의
        JobDetail jobDetail = JobBuilder.newJob(QuartzJob.class)
                .withIdentity(jobKey)
                .usingJobData(JOB_NAME, jobName)
                .build();

        // Trigger 정의
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName + "Trigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                .forJob(jobDetail)
                .build();

        // Job + Trigger 등록
        scheduler.scheduleJob(jobDetail, trigger);
    }

    /**
     * Job 중지
     * @param jobName 중지할 Job 이름
     */
    public void stopJob(String jobName) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName);
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }
    }

    /**
     * Job 일시정지
     * @param jobName 일시정지할 Job 이름
     */
    public void pauseJob(String jobName) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName);
        if (scheduler.checkExists(jobKey)) {
            scheduler.pauseJob(jobKey);
        }
    }

    /**
     * Job 재시작 (resume)
     * @param jobName 재시작할 Job 이름
     */
    public void resumeJob(String jobName) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName);
        if (scheduler.checkExists(jobKey)) {
            scheduler.resumeJob(jobKey);
        }
    }

    /**
     * Cron 표현식 업데이트 (Trigger 재스케줄)
     * @param jobName 대상 Job 이름
     * @param newCron 새 Cron 표현식 (예: "0/10 * * * * ?")
     */
    public void updateJobCron(String jobName, String newCron) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(jobName + "Trigger");
        JobKey jobKey = JobKey.jobKey(jobName);

        if (!scheduler.checkExists(triggerKey)) {
            throw new SchedulerException("Trigger not found");
        }

        // 새 Trigger 생성
        Trigger newTrigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(newCron))
                .forJob(jobKey)
                .build();

        // 기존 트리거 재등록
        scheduler.rescheduleJob(triggerKey, newTrigger);
    }
}

//---------------------------------------
@Service
@RequiredArgsConstructor
public class QuartzJobService {

    private final Scheduler scheduler;

    public void scheduleJob(String jobName, String jobGroup, String cron, Class<? extends Job> jobClass) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, jobGroup);

        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey); // 기존 삭제
        }

        JobDetail jobDetail = QuartzJobUtil.buildJobDetail(jobClass, jobName, jobGroup);
        Trigger trigger = QuartzJobUtil.buildCronTrigger(jobName, jobGroup, cron);

        scheduler.scheduleJob(jobDetail, trigger);
    }

    public void updateTrigger(String jobName, String jobGroup, String newCron) throws SchedulerException {
        TriggerKey triggerKey = new TriggerKey(jobName + "Trigger", jobGroup);
        Trigger newTrigger = QuartzJobUtil.buildCronTrigger(jobName, jobGroup, newCron);
        scheduler.rescheduleJob(triggerKey, newTrigger);
    }

    public void pauseJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.pauseJob(new JobKey(jobName, jobGroup));
    }

    public void resumeJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.resumeJob(new JobKey(jobName, jobGroup));
    }

    public void deleteJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.deleteJob(new JobKey(jobName, jobGroup));
    }
}


//------------------------------------------------
public class QuartzJobUtil {

    public static JobDetail buildJobDetail(Class<? extends Job> jobClass, String jobName, String jobGroup) {
        return JobBuilder.newJob(jobClass)
                .withIdentity(jobName, jobGroup)
                .storeDurably()
                .build();
    }

    public static Trigger buildCronTrigger(String jobName, String jobGroup, String cronExpression) {
        return TriggerBuilder.newTrigger()
                .withIdentity(jobName + "Trigger", jobGroup)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .forJob(jobName, jobGroup)
                .build();
    }
}
//---------------------------------------------
@Configuration
public class QuartzConfig {

    @Bean
    public SpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext) {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(SpringBeanJobFactory jobFactory,
                                                     Trigger asyncBatchTrigger,
                                                     JobDetail asyncBatchJobDetail) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(jobFactory);
        factory.setJobDetails(asyncBatchJobDetail);
        factory.setTriggers(asyncBatchTrigger);
        return factory;
    }
}

//----------------------------------------------------------
@Component
public class AsyncBatchQuartzJob implements Job {

    private final JobLauncher jobLauncher;
    private final Job batchJob;

    @Autowired
    public AsyncBatchQuartzJob(JobLauncher jobLauncher, @Qualifier("myJob") Job batchJob) {
        this.jobLauncher = jobLauncher;
        this.batchJob = batchJob;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        try {
            jobLauncher.run(batchJob, params);
        } catch (Exception e) {
            throw new JobExecutionException("Job 실행 중 오류", e);
        }
    }
}


@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BatchJobTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("myJob") // 대상 Job 이름
    private Job job;

    @Test
    public void testBatchJobExecution() throws Exception {
        // Given: Job 파라미터 구성
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // When: Job 실행
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then: 결과 검증
        Assertions.assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
    }
}


