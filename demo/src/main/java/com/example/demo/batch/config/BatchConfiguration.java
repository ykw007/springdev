package com.example.demo.batch.config;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Configuration
public class BatchConfiguration {

	private static final Logger log = LoggerFactory.getLogger(BatchConfiguration.class);
	
    private final DataSource dataSource;

	@Autowired
	private FlatFileItemReader<Person> personItemReader;
	
	@Autowired
	private JdbcBatchItemWriter<Person> personItemWriter;

	@Bean("partitioner")
	//@StepScope
	public Partitioner partitioner() {
		log.info("In Partitioner");

		MultiResourcePartitioner partitioner = new MultiResourcePartitioner();
		ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource[] resources = null;
		try {
			resources = resolver.getResources("file:/C:/Temp/*.csv");
			
			int s = resources.length;
			
			log.info(">>>>>>>>>>>>>>>>>>>>>>>> resource size : "+s);;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		partitioner.setResources(resources);
		partitioner.partition(10);
		return partitioner;
	}

	@Bean
	public PersonItemProcessor processor() {
		return new PersonItemProcessor();
	}

    @Bean
    @StepScope // 각 스레드마다 런타임 시점에 각각의 itemWriter 를 생성해서 할당
    // 하나의 스레드가 입력해도 상관은 없는데, 만약 데이터가 작으면 main 스레드가 작업해도 상관은 없음
    // 우선 @StepScope 를 선언하여 ItemWriter 도 여러 스레드로 수행하자.
	@Qualifier("personItemWriter")
	@DependsOn("partitioner")
    public JdbcBatchItemWriter<Person> partitioningCustomerItemWriter() {
        JdbcBatchItemWriter<Person> itemWriter = new JdbcBatchItemWriter<>();

        itemWriter.setDataSource(this.dataSource);
        itemWriter.setSql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)");
        itemWriter.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider());
        itemWriter.afterPropertiesSet();

        return itemWriter;
    }

	@Bean(name = "importUserJob")
	public Job importUserJob(JobRepository jobRepository, PlatformTransactionManager transactionManager
			, JobCompletionNotificationListener listener, Step step1) {
		
		 return new JobBuilder("importUserJob", jobRepository) 
				 //.incrementer(new RunIdIncrementer()) 
				 .listener(listener)
				 .start(masterStep(jobRepository, transactionManager))
				 //.end()
				 .build();
		 
		
        //return new JobBuilder("importUserJob", jobRepository)
        //        .start(masterStep(jobRepository, transactionManager))
        //        .build();
	}

	@Bean
	public Step step1(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		return new StepBuilder("step1", jobRepository)
				.<Person, Person>chunk(10, transactionManager)
				.processor(processor())
				.writer(personItemWriter)
				.reader(personItemReader)
				.build();
	}

	@Bean
	public ThreadPoolTaskExecutor taskExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setMaxPoolSize(20);
		taskExecutor.setCorePoolSize(10);
		taskExecutor.setQueueCapacity(10);
		taskExecutor.afterPropertiesSet();
		return taskExecutor;
	}

	@Bean
	@Qualifier("masterStep")
	public Step masterStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		return new StepBuilder("masterStep", jobRepository)
				.partitioner("step1", partitioner())
				.step(step1(jobRepository, transactionManager))
				.taskExecutor(taskExecutor())
				.gridSize(10)
				.build();
	}

	@Bean
	@StepScope
	@Qualifier("personItemReader")
	@DependsOn("partitioner")
	public FlatFileItemReader<Person> personItemReader(@Value("#{stepExecutionContext['fileName']}") String filename)
			throws MalformedURLException {
		log.info("In Reader");
		return new FlatFileItemReaderBuilder<Person>().name("personItemReader")
				.delimited()
				.names(new String[] { "firstName", "lastName" })
				.fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {
					{
						setTargetType(Person.class);
					}
				})
				.resource(new UrlResource(filename))
				.build();
	}
}
