// ItemWriter – 멀티스레드 처리
@Component
public class MultiThreadWriter implements ItemWriter<Product> {

    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    @Override
    public void write(List<? extends Product> items) throws Exception {
        List<Future<?>> futures = new ArrayList<>();
        for (Product p : items) {
            futures.add(executor.submit(() -> {
                System.out.printf("Thread: %s >> %s%n", Thread.currentThread().getName(), p.getName());
                // 예: 외부 API 처리
            }));
        }

        for (Future<?> f : futures) {
            f.get(); // 예외 캐치
        }
    }
}


// Spring Batch Job 설정
@Configuration
@RequiredArgsConstructor
public class BatchJobConfig {

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final MultiThreadQueueReader reader;
    private final MultiThreadWriter writer;

    @Bean
    public Job myBatchJob() {
        return jobBuilderFactory.get("myAsyncBatchJob")
                .start(step())
                .build();
    }

    @Bean
    public Step step() {
        return stepBuilderFactory.get("multiStep")
                .<Product, Product>chunk(10)
                .reader(reader)
                .writer(writer)
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


// Quartz Job 클래스 (비동기 실행)
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
public class QuartzAsyncBatchJobLauncher implements org.quartz.Job {

    private final JobLauncher jobLauncher;
    private final Job myBatchJob;

    public QuartzAsyncBatchJobLauncher(JobLauncher jobLauncher, Job myBatchJob) {
        this.jobLauncher = jobLauncher;
        this.myBatchJob = myBatchJob;
    }

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


// Spring 지원 JobFactory 설정
public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

    private transient AutowireCapableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(final ApplicationContext context) {
        this.beanFactory = context.getAutowireCapableBeanFactory();
    }

    @Override
    protected Object createJobInstance(final TriggerFiredBundle bundle) throws Exception {
        final Object job = super.createJobInstance(bundle);
        beanFactory.autowireBean(job); // Spring 의존성 주입
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
