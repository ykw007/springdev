package com.example.batch;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;

import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;

@SpringBatchTest
@SpringBootTest(properties = {
        "spring.batch.job.enabled=false" // 테스트 시 자동실행 방지
})
@ActiveProfiles("test") // src/test/resources/application-test.yml 사용
class BJobOnlyTest {

    @Autowired private DataSource dataSource;
    @Autowired private ResourceLoader resourceLoader;
    @Autowired private JdbcTemplate jdbcTemplate;

    // 여기서 주입되는 utils는 아래 @TestConfiguration에서 bJob으로 고정한 Bean
    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;

    @BeforeEach
    void setUp() throws Exception {
        runSql("classpath:sql/insert.sql");
    }

    @AfterEach
    void tearDown() throws Exception {
        runSql("classpath:sql/delete.sql");
    }

    @Test
    void bJob_runs_and_completes() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("ts", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        org.assertj.core.api.Assertions.assertThat(execution.getExitStatus())
                .as("bJob should complete successfully")
                .isEqualTo(ExitStatus.COMPLETED);

        // (선택) 결과 검증 예시
        // int count = jdbcTemplate.queryForObject(
        //         "select count(*) from target_table where test_flag = 1", Integer.class);
        // org.assertj.core.api.Assertions.assertThat(count).isGreaterThan(0);
    }

    private void runSql(String location) throws Exception {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("SQL resource not found: " + location);
        }
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, resource);
        }
    }

    /**
     * 테스트용 설정:
     * - 여러 Job 빈(aJob, bJob) 중에서 bJob만 실행하도록 JobLauncherTestUtils를 재정의
     * - @Primary로 @SpringBatchTest가 제공하는 기본 JobLauncherTestUtils보다 우선 적용
     */
    @TestConfiguration
    static class BJobTestConfig {

        @Bean
        @Primary
        JobLauncherTestUtils bJobLauncherTestUtils(
                @Qualifier("bJob") Job bJob,
                JobLauncher jobLauncher,
                JobRepository jobRepository
        ) {
            JobLauncherTestUtils utils = new JobLauncherTestUtils();
            utils.setJob(bJob);                 // ★ bJob만 실행하도록 고정
            utils.setJobLauncher(jobLauncher);
            utils.setJobRepository(jobRepository);
            return utils;
        }
    }
}
