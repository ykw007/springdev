package com.example.quartz.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * @brief Spring Boot의 application.yml 내 spring.quartz.properties 설정을 자동으로 읽어
 *        평탄한 형태의 Properties 객체로 변환해주는 유틸리티 클래스
 *
 * @details
 * Spring Boot의 계층형 설정(YAML)은 Map 형태로 바인딩되므로,
 * 이를 Quartz에서 요구하는 key=value 형태의 Properties 객체로 변환해야 합니다.
 *
 * 예: spring.quartz.properties.org.quartz.threadPool.threadCount → org.quartz.threadPool.threadCount
 *
 * 이 클래스는 SchedulerFactoryBean 등에 전달 가능한 Properties 객체를 자동 구성해줍니다.
 */
@Component
public class QuartzPropertiesBinder {

    // 변환된 Quartz Properties를 저장할 객체
    private final Properties quartzProperties = new Properties();

    /**
     * @brief 생성자에서 Environment로부터 spring.quartz.properties 바인딩
     * @param environment Spring의 환경 설정 객체
     */
    public QuartzPropertiesBinder(Environment environment) {
        // spring.quartz.properties 이하 계층 구조를 Map으로 바인딩
        Map<String, Object> allProperties = Binder.get(environment)
                .bind("spring.quartz.properties", Map.class)
                .orElse(Collections.emptyMap());

        // 계층형 Map을 평탄화하여 Properties에 저장
        flatten("", allProperties, quartzProperties);
    }

    /**
     * @return Quartz 설정을 담은 평탄화된 Properties 객체
     */
    public Properties getQuartzProperties() {
        return quartzProperties;
    }

    /**
     * @brief 계층형 Map을 평탄화(flatten)하여 Properties로 변환
     *
     * @param prefix 현재까지의 키 prefix (재귀적으로 누적)
     * @param source 원본 Map (계층 구조)
     * @param target 변환된 Properties 대상
     */
    private void flatten(String prefix, Map<String, Object> source, Properties target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                // 하위 Map이 존재하면 재귀 호출
                flatten(key, (Map<String, Object>) value, target);
            } else {
                // 평탄화된 키=값 형태로 Properties에 저장
                target.setProperty(key, value.toString());
            }
        }
    }
}



package com.example.quartz.config;

import org.quartz.spi.JobFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

/**
 * @brief Quartz Scheduler 설정 클래스
 *
 * @details application.yml의 spring.quartz.properties 항목을 바탕으로 SchedulerFactoryBean 설정
 */
@Configuration
public class QuartzConfig {

    private final QuartzPropertiesBinder quartzPropertiesBinder;

    public QuartzConfig(QuartzPropertiesBinder binder) {
        this.quartzPropertiesBinder = binder;
    }

    /**
     * @brief SchedulerFactoryBean 설정
     *
     * @return Quartz Scheduler 팩토리 빈
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();

        // application.yml → spring.quartz.properties 항목을 반영
        factory.setQuartzProperties(quartzPropertiesBinder.getQuartzProperties());

        return factory;
    }
}
