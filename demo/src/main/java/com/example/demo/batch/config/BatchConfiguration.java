package com.example.demo.batch.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.mapping.PatternMatchingCompositeLineMapper;
import org.springframework.batch.item.file.transform.DefaultFieldSet;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.LineTokenizer;
import org.springframework.batch.item.file.transform.PatternMatchingCompositeLineTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.batch.item.support.SingleItemPeekableItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Configuration
public class BatchConfiguration {

	private static final Logger log = LoggerFactory.getLogger(BatchConfiguration.class);
	
    private final DataSource dataSource;
    
	private final PatternMatchingCompositeLineMapper<SampleData> mapper = new PatternMatchingCompositeLineMapper<>();

	@Autowired
	private FlatFileItemReader<SampleData> personItemReader;
	
	@Autowired
	private JdbcBatchItemWriter<SampleData> personItemWriter;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @StepScope
	@Bean("partitioner")
	Partitioner partitioner() {
		log.info("In Partitioner");
		
		
		jdbcTemplate.query("SELECT first_name as c1, last_name as c2, '' as c3, '' as c4, '' as c5, '' as c6 FROM people limit 1",
					(rs, row) -> new SampleData(
						rs.getString(1),
						rs.getString(2),
						rs.getString(3),
						rs.getString(4),
						rs.getString(5),
						rs.getString(6)
						)
				).forEach(person -> log.info("Found <" + person + "> in the database."));

		MultiResourcePartitioner partitioner = new MultiResourcePartitioner();
		ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource[] resources = null;
		try {
			resources = resolver.getResources("./input/*.*");
			
			int s = resources.length;
			
			log.info(">>>>>>>>>>>>>>>>>>>>>>>> resource size : "+s);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		partitioner.setResources(resources);
		partitioner.partition(10);
		return partitioner;
	}
    
	@Bean(name = "importUserJob")
	Job importUserJob(JobRepository jobRepository, PlatformTransactionManager transactionManager
			, JobCompletionNotificationListener listener, Step step1) {
		
		 return new JobBuilder("importUserJob", jobRepository) 
				 //.incrementer(new RunIdIncrementer()) 
				 .listener(listener)
				 .start(masterStep(jobRepository, transactionManager))
				 .build();
		 
		
        //return new JobBuilder("importUserJob", jobRepository)
        //        .start(masterStep(jobRepository, transactionManager))
        //        .build();
	}
	
	@Bean
	@Qualifier("masterStep")
	Step masterStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		return new StepBuilder("masterStep", jobRepository)
				.partitioner("step1", partitioner())				
				.step(step1(jobRepository, transactionManager))
				.taskExecutor(taskExecutor())
				.gridSize(10)
				.build();
	}
	
	
	@Bean
	Step step1(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		return new StepBuilder("step1", jobRepository)
				.<SampleData, SampleData>chunk(10, transactionManager)
				.reader(personItemReader)
				.processor(processor())
				.writer(personItemWriter)
				.build();
	}

	@Bean
	@StepScope
	@Qualifier("personItemReader")
	@DependsOn("partitioner")
	FlatFileItemReader<SampleData> personItemReader(@Value("#{stepExecutionContext['fileName']}") String filename)
			throws Exception {
		log.info("In Reader" + filename);
		FlatFileItemReader<SampleData> ffr = null;
		
		if(filename.lastIndexOf(".csv") > 0) {
			ffr = new FlatFileItemReaderBuilder<SampleData>().name("personItemReader")
			.delimited()
			.names(new String[] { "c1", "c2" })
			.fieldSetMapper(new BeanWrapperFieldSetMapper<SampleData>() {
				{
					setTargetType(SampleData.class);
				}
			})
			.resource(new UrlResource(filename))
			.build();
		}
		else if(filename.lastIndexOf(".txt") > 0) {
			
			ffr = new FlatFileItemReaderBuilder<SampleData>().name("personItemReader")
					.resource(new UrlResource(filename))
					.linesToSkip(2)
					.lineMapper(productLineMapper())
					.build();
		}
		
		return ffr;
	}

    // https://github.com/debop/spring-batch-experiments/blob/master/chapter05/src/test/java/kr/spring/batch/chapter05/test/file/JobStructureDelimitedMultiFlatFileConfig.java
	@Bean
	public LineMapper<SampleData> productLineMapper() throws Exception {
		// HINT: 한 파일에 여러 종류의 데이터가 혼재해 있을 때 씁니다.

		PatternMatchingCompositeLineMapper<SampleData> mapper = new PatternMatchingCompositeLineMapper<>();

		Map<String, LineTokenizer> tokenizers = new HashMap<String, LineTokenizer>();
		tokenizers.put("PR*", mobilePhoneProductLineTokenizer());
		tokenizers.put("P2*", mobilePhoneProductLineTokenizer());
		mapper.setTokenizers(tokenizers);

		Map<String, FieldSetMapper<SampleData>> mappers = new HashMap<String, FieldSetMapper<SampleData>>();
		mappers.put("PR*", mobilePhoneProductFieldSetMapper());
		mappers.put("P2*", mobilePhoneProductFieldSetMapper());
		mapper.setFieldSetMappers(mappers);

		return mapper;
	}
	
	@Bean
	public LineTokenizer mobilePhoneProductLineTokenizer() {
		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(",");
		tokenizer.setNames(new String[] { "c1", "c2", "c3", "c4" });
		return tokenizer;
	}

	@Bean
	public LineTokenizer P2LineTokenizer() {
		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(",");
		tokenizer.setNames(new String[] { "c5", "c6" });
		return tokenizer;
	}

	@Bean
	public FieldSetMapper<SampleData> mobilePhoneProductFieldSetMapper() throws Exception {
		BeanWrapperFieldSetMapper<SampleData> mapper =
				new BeanWrapperFieldSetMapper<SampleData>();

		mapper.setPrototypeBeanName("mobilePhoneProduct");
		mapper.afterPropertiesSet();
		return mapper;
	}

	@Bean
	public FieldSetMapper<SampleData> P2FieldSetMapper() throws Exception {
		BeanWrapperFieldSetMapper<SampleData> mapper =
				new BeanWrapperFieldSetMapper<SampleData>();

		mapper.setPrototypeBeanName("mobilePhoneProduct");
		mapper.afterPropertiesSet();
		return mapper;
	}
	
	@Bean
	@Scope("prototype")
	public SampleData mobilePhoneProduct() {
		return new SampleData();
	}
	/*
	@Bean
	@Scope("prototype")
	public BookProduct bookProduct() {
		return new BookProduct();
	}
	*/
	@Bean
	@StepScope
	SampleDataItemProcessor processor() {
		return new SampleDataItemProcessor();
	}


    @Bean
    @StepScope // 각 스레드마다 런타임 시점에 각각의 itemWriter 를 생성해서 할당
    // 하나의 스레드가 입력해도 상관은 없는데, 만약 데이터가 작으면 main 스레드가 작업해도 상관은 없음
    // 우선 @StepScope 를 선언하여 ItemWriter 도 여러 스레드로 수행하자.
	@Qualifier("personItemWriter")
	@DependsOn("partitioner")
    JdbcBatchItemWriter<SampleData> personItemWriter(@Value("#{stepExecutionContext['fileName']}") String filename) {
    	
    	log.info("In personItemWriter : " + filename);
        JdbcBatchItemWriter<SampleData> itemWriter = new JdbcBatchItemWriter<>();

        itemWriter.setDataSource(this.dataSource);
		if(filename.lastIndexOf(".csv") > 0) {        
			itemWriter.setSql("INSERT INTO people (first_name, last_name) VALUES (:c1, :c2)");
		}else if(filename.lastIndexOf(".txt") > 0) {
			itemWriter.setSql("INSERT INTO tbl_test (c1, c2, c3, c4, c5, c6) VALUES (:c1, :c2, :c3, :c4, :c5, :c6)");
		}
        itemWriter.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider());
        itemWriter.afterPropertiesSet();

        return itemWriter;
    }


	@Bean
	ThreadPoolTaskExecutor taskExecutor() {

		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setMaxPoolSize(20);
		taskExecutor.setCorePoolSize(10);
		taskExecutor.setQueueCapacity(10);
		taskExecutor.afterPropertiesSet();

		return taskExecutor;
	}

}
