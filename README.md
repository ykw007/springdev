# springdev
springdev
@Configuration
public class BatchConfig {

    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        SimpleJobLauncher jobLauncher = new SimpleJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(taskExecutor()); // 비동기 실행
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);     // 필요에 따라 조정
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("BatchExecutor-");
        executor.initialize();
        return executor;
    }
}

@Component
public class AsyncBatchQuartzJob implements Job {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job sampleJob;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        try {
            jobLauncher.run(sampleJob, params);
        } catch (Exception e) {
            throw new JobExecutionException("Batch Job 실행 중 오류 발생", e);
        }
    }
}

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail asyncBatchJobDetail() {
        return JobBuilder.newJob(AsyncBatchQuartzJob.class)
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