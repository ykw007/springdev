package com.example.demo.batch.config;


import lombok.extern.slf4j.Slf4j;


import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.configuration.support.JobRegistryBeanPostProcessor;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Configuration
public class ExampleJobConfiguration {
    private static final String JOB_NAME = "exampleJob";
    private static final String STEP_NAME = "exampleJobStep";

    @Bean
	public JobRegistryBeanPostProcessor jobRegistryBeanPostProcessor(JobRegistry jobRegistry) {
		JobRegistryBeanPostProcessor jobRegistryBeanPostProcessor = new JobRegistryBeanPostProcessor();
		jobRegistryBeanPostProcessor.setJobRegistry(jobRegistry);
		return jobRegistryBeanPostProcessor;
	}
    
    @Bean
    public Job exampleJob1(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(exampleJob1Step(jobRepository, transactionManager))
                .build();
    }
    
    private Step exampleJob1Step(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<String, String> chunk(2, transactionManager)
                .reader(exampleJob1Reader())
                .processor(exampleJob1Processor())
                .writer(exampleJob1Writer())
                .build();
    }
    
    @Bean
    @StepScope
    public ItemReader<String> exampleJob1Reader() {
        return new ItemReader<String>() {
            private List<String> sampleData;
            private int count;

            @Override
            public String read() throws Exception {
                fetch();
                return next();
            }

            private String next() {
                if (this.count >= this.sampleData.size()) {
                    return null;
                }
                return this.sampleData.get(count++);
            }

            private void fetch() {
                if(isInitialized()){
                    return;
                }
                this.sampleData = IntStream.range(0, 20).boxed().map(String::valueOf).map(s -> s + "-read").collect(Collectors.toList());
            }

            private boolean isInitialized() {
                return this.sampleData != null;
            }
        };
    }
    
    private ItemProcessor<String, String> exampleJob1Processor() {
        return item -> item + "-processing";
    }

    private ItemWriter<String> exampleJob1Writer() {
        return items -> items.getItems().stream().map(o -> "[" + JOB_NAME + "] " + o + "-write").forEach(log::info);
    }
}
