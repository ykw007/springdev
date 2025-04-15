// ItemWriter – 멀티스레드 처리
package com.example.demo.batch.writer;

import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.*;

/**
 * MultiThreadWriter
 * Spring Batch의 ItemWriter 구현으로, 각 아이템을 멀티스레드 방식으로 병렬 처리합니다.
 * - 병렬 스레드 처리는 ExecutorService를 사용합니다.
 * - 쓰레드 수는 고정(FixedThreadPool)이며, Future를 통해 예외 발생 여부를 감지할 수 있습니다.
 */
@Component
public class MultiThreadWriter implements ItemWriter<String> {

    // 병렬 처리를 위한 고정 스레드풀 (5개 스레드)
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    /**
     * Spring Batch가 chunk 단위로 호출하는 write 메서드
     * @param items 현재 chunk에서 전달된 아이템 리스트
     */
    @Override
    public void write(List<? extends String> items) throws Exception {
        List<Future<?>> futures = new ArrayList<>();

        // 각 아이템을 병렬로 처리
        for (String item : items) {
            Future<?> future = executor.submit(() -> {
                // 실제 처리 로직 (여기서는 단순 출력)
                System.out.printf("Thread: %s - Writing item: %s%n",
                        Thread.currentThread().getName(), item);

                // 예: 외부 API 호출, DB 처리 등 시간이 걸리는 작업 가능
            });

            futures.add(future);
        }

        // 모든 작업이 완료될 때까지 대기하고 예외를 캐치
        for (Future<?> future : futures) {
            try {
                future.get(); // 작업 성공 여부 확인
            } catch (ExecutionException e) {
                // 병렬 작업 중 예외 발생 시 상위로 전파
                throw new Exception("Item 처리 중 예외 발생", e.getCause());
            }
        }
    }

    /**
     * 종료 시 호출할 자원 정리 메서드 (선택적으로 사용)
     */
    public void shutdown() {
        executor.shutdown();
    }
}


//-----------------------------------------------------
// Spring Batch Job 설정
package com.example.demo.batch.config;

import com.example.demo.batch.reader.MultiThreadQueueReader;
import com.example.demo.batch.writer.MultiThreadWriter;
import com.example.demo.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * BatchJobConfig
 * Spring Batch Job 설정 클래스입니다.
 * - 하나의 Job 안에 Step이 구성되어 있으며,
 * - Reader → (Processor 생략) → Writer 형태로 실행됩니다.
 * - MultiThreadQueueReader와 MultiThreadWriter를 사용합니다.
 */
@Configuration
@RequiredArgsConstructor
public class BatchJobConfig {

    // Job과 Step 생성을 위한 Factory 주입 (Spring에서 제공)
    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;

    private final MultiThreadQueueReader reader;    // 커스텀 ItemReader (MyBatis + Queue 기반)
    private final MultiThreadWriter writer;         // 병렬 처리용 커스텀 Writer

    /**
     * 배치 Job 정의
     * @return Job 인스턴스
     */
    @Bean
    public Job myBatchJob() {
        return jobBuilderFactory.get("multiThreadedBatchJob") // Job 이름
                .start(step())                                // 시작 Step 지정
                .build();                                     // Job 구성 완료
    }

    /**
     * Step 정의 (Chunk 기반 처리)
     * - ItemReader: 큐 기반 (MyBatis 조회 → Queue 적재)
     * - ItemProcessor: 생략
     * - ItemWriter: 병렬 처리 Writer
     * @return Step 인스턴스
     */
    @Bean
    public Step step() {
        return stepBuilderFactory.get("multiThreadStep")      // Step 이름
                .<Product, Product>chunk(10)                  // Chunk 크기 지정 (10개 단위)
                .reader(reader)                               // 커스텀 Reader 지정
                .writer(writer)                               // 커스텀 Writer 지정
                .build();
    }
}


// Async JobLauncher 설정
@Configuration
public class AsyncBatchLauncherConfig {

    @Bean(name = "asyncJobLauncher")
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) {
        SimpleJobLauncher jobLauncher = new SimpleJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor("batch-async-"));
        return jobLauncher;
    }
}

//-------------------------------------------------------
// Quartz Job 클래스 (비동기 실행)
package com.example.demo.quartz;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobLocator;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * QuartzJob
 * Quartz에서 실행되는 Job 클래스이며, 내부적으로 Spring Batch Job을 비동기로 실행합니다.
 * - 파일 시스템에서 데이터를 읽고, 각 파일을 기준으로 Spring Batch Job 실행
 * - Job 실행 시 TaskExecutorJobLauncher 사용하여 비동기 처리
 * - JobDataMap에서 전달된 jobName을 기반으로 실제 Job을 동적으로 찾음
 */
@Slf4j
public class QuartzJob extends QuartzJobBean implements InterruptableJob {

    private static final String JOB_NAME = "JOB_NAME";

    private volatile boolean isJobInterrupted = false;
    private volatile Thread currThread;

    // Spring Batch JobLocator: Job 이름으로 Job 객체를 찾기 위해 사용
    @Autowired
    private JobLocator jobLocator;

    // Spring Batch JobRepository: Job 실행 기록 관리
    @Autowired
    private JobRepository jobRepository;

    /**
     * Quartz에서 스케줄링 시 호출되는 핵심 메서드
     * - 지정된 디렉토리의 파일 목록을 읽고, 각 파일을 기준으로 Spring Batch Job을 비동기 실행함
     */
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            String jobName = context.getJobDetail().getJobDataMap().getString(JOB_NAME);
            log.info("Quartz Job 실행 시작 - jobName: {}", jobName);

            // TaskExecutor 기반 비동기 JobLauncher 생성
            TaskExecutorJobLauncher asyncLauncher = new TaskExecutorJobLauncher();
            asyncLauncher.setJobRepository(jobRepository);
            asyncLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor());
            asyncLauncher.afterPropertiesSet();

            // 지정된 디렉토리에서 최근 수정된 최대 100개 파일 조회
            Files.walk(Paths.get("./src/main/resources/input/"))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .sorted((a, b) -> Long.compare(a.lastModified(), b.lastModified()))
                    .limit(100)
                    .forEachOrdered(file -> {
                        try {
                            log.info("파일 처리 시작: {}", file.getName());

                            // 파일명을 파라미터로 전달하여 Spring Batch Job 실행
                            JobParametersBuilder params = new JobParametersBuilder()
                                    .addLong("currTime", System.nanoTime())
                                    .addString("filename", file.getName());

                            Job job = jobLocator.getJob(jobName); // Job 이름으로 실제 Job 찾기
                            asyncLauncher.run(job, params.toJobParameters());

                        } catch (Exception e) {
                            log.error("Spring Batch Job 실행 중 오류 발생 - 파일: {}", file.getName(), e);
                        }
                    });

        } catch (Exception e) {
            log.error("QuartzJob 실행 중 예외 발생", e);
        }
    }

    /**
     * Job 인터럽트 처리 메서드
     * - Job이 실행 중일 때 외부에서 중지 요청이 들어오면 인터럽트를 걸 수 있음
     */
    @Override
    public void interrupt() throws UnableToInterruptJobException {
        isJobInterrupted = true;
        if (currThread != null) {
            log.info("현재 실행 중인 쓰레드 인터럽트 요청: {}", currThread.getName());
            currThread.interrupt();
        }
    }
}

//---------
// 2
@Component
public class QuartzAsyncBatchJobLauncher implements org.quartz.Job {

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    private Job myBatchJob;

    @Override
    public void execute(JobExecutionContext context) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("createDate", LocalDate.now().toString())
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(myBatchJob, params);
        } catch (Exception e) {
            throw new RuntimeException("비동기 Batch Job 실행 실패", e);
        }
    }
}


// Quartz Job 스케줄링 설정
@Configuration
public class QuartzScheduleConfig {

    @Bean
    public JobDetail asyncBatchJobDetail() {
        return JobBuilder.newJob(QuartzAsyncBatchJobLauncher.class)
                .withIdentity("asyncBatchJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger asyncBatchTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(asyncBatchJobDetail())
                .withIdentity("asyncBatchTrigger")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(60)
                        .repeatForever())
                .build();
    }
}


// QuartzAsyncBatchJobLauncher – 생성자 주입 방식
package com.example.demo.quartz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * QuartzAsyncBatchJobLauncher
 * - Quartz의 Job 인터페이스를 구현한 클래스
 * - Spring Batch의 JobLauncher를 생성자 주입 방식으로 받아 비동기 실행
 * - Scheduler가 트리거되면 지정된 Spring Batch Job을 실행함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuartzAsyncBatchJobLauncher implements Job {

    private final JobLauncher jobLauncher;  // Spring Batch 비동기 실행용 런처
    private final Job batchJob;             // 실행할 Spring Batch Job

    /**
     * Quartz Job 실행 메서드
     * - Scheduler가 트리거될 때마다 호출됨
     * - JobParameters를 유일하게 설정하여 중복 실행 방지
     * @param context Quartz 실행 컨텍스트
     * @throws JobExecutionException 실행 중 예외 발생 시 throw
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // Job 파라미터 구성: 중복 실행 방지를 위해 유니크한 값 포함
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis()) // 유일성 보장
                    .addString("triggeredAt", LocalDateTime.now().toString())
                    .toJobParameters();

            log.info("Quartz Job 실행 시작 - 파라미터: {}", params);

            // Spring Batch Job 실행 (비동기 또는 동기 Launcher 사용 가능)
            jobLauncher.run(batchJob, params);

            log.info("Quartz Job 실행 완료");

        } catch (Exception e) {
            log.error("Batch Job 실행 중 예외 발생", e);
            throw new JobExecutionException("Batch Job 실행 실패", e);
        }
    }
}
Spring Batch Job 이름을 JobLocator로 동적으로 지정하고 싶을 경우에는 JobDataMap 사용이 필요
@DisallowConcurrentExecution 어노테이션으로 중복 실행 방지도 설정 가능
@Component로 등록되므로 JobFactory가 Spring Context에서 생성하도록 해야 함



//-------------------------------------------
// Spring 지원 JobFactory 설정
package com.example.demo.quartz;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * AutowiringSpringBeanJobFactory
 * - Quartz의 기본 JobFactory(SpringBeanJobFactory)를 확장한 클래스
 * - Spring ApplicationContext를 주입받아 Quartz Job에 자동으로 의존성 주입을 적용함
 * - Quartz가 생성하는 Job 인스턴스에도 @Autowired 등의 DI 기능을 제공 가능
 */
public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

    // Spring의 의존성 주입을 위해 사용되는 BeanFactory
    private transient AutowireCapableBeanFactory beanFactory;

    /**
     * ApplicationContext 주입 시 호출됨
     * @param applicationContext 현재 Spring Context
     * @throws BeansException 예외 발생 시 처리
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 의존성 주입을 수행할 수 있는 BeanFactory 설정
        this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
    }

    /**
     * Quartz가 Job 인스턴스를 생성할 때 호출되는 메서드
     * - Job 인스턴스를 생성한 뒤, Spring의 의존성 주입을 적용함
     * @param bundle 트리거된 Job 실행 정보
     * @return DI가 적용된 Job 인스턴스
     * @throws Exception 예외 발생 시 처리
     */
    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        // 기본 Job 인스턴스 생성
        final Object job = super.createJobInstance(bundle);
        // Spring Bean 자동 주입 적용
        beanFactory.autowireBean(job);
        return job;
    }
}




// Quartz Scheduler에 커스텀 JobFactory 적용
@Configuration
public class QuartzSchedulerConfig {

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(AutowiringSpringBeanJobFactory jobFactory,
                                                      Trigger asyncBatchTrigger,
                                                      JobDetail asyncBatchJobDetail) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(jobFactory);
        factory.setJobDetails(asyncBatchJobDetail);
        factory.setTriggers(asyncBatchTrigger);
        return factory;
    }

    @Bean
    public AutowiringSpringBeanJobFactory jobFactory(ApplicationContext applicationContext) {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }
}

---------------------------------------------
@RestController
@RequestMapping("/api/quartz")
@RequiredArgsConstructor
public class QuartzControlController {

    private final Scheduler scheduler;

    @PostMapping("/start")
    public ResponseEntity<String> startJob() throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(QuartzConstants.JOB_NAME, QuartzConstants.JOB_GROUP);

        if (!scheduler.checkExists(jobKey)) {
            JobDetail jobDetail = JobBuilder.newJob(QuartzAsyncBatchJobLauncher.class)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .build();

            CronScheduleBuilder cron = CronScheduleBuilder.cronSchedule("0 0/1 * * * ?"); // 매 1분
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(QuartzConstants.TRIGGER_NAME, QuartzConstants.TRIGGER_GROUP)
                    .withSchedule(cron)
                    .forJob(jobDetail)
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            return ResponseEntity.ok("Job created and scheduled.");
        } else {
            return ResponseEntity.ok("Job already exists.");
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stopJob() throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(QuartzConstants.TRIGGER_NAME, QuartzConstants.TRIGGER_GROUP);

        if (scheduler.checkExists(triggerKey)) {
            scheduler.unscheduleJob(triggerKey);
            return ResponseEntity.ok("Job unscheduled.");
        } else {
            return ResponseEntity.ok("Trigger not found.");
        }
    }

    @PostMapping("/update-cron")
    public ResponseEntity<String> updateCron(@RequestParam String cronExpression) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(QuartzConstants.TRIGGER_NAME, QuartzConstants.TRIGGER_GROUP);

        if (!scheduler.checkExists(triggerKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trigger not found.");
        }

        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression);
        CronTrigger newTrigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(scheduleBuilder)
                .build();

        scheduler.rescheduleJob(triggerKey, newTrigger);
        return ResponseEntity.ok("Trigger updated.");
    }
}
