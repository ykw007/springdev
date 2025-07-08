////////////////////////////////////////////////////////////////////////////////////////
package com.example.aspect;

import com.example.config.StopWatchProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class QueryServiceAspect {

    private final StopWatchProperty stopWatchProperty;

    @Pointcut("@annotation(com.example.aspect.logDisplay)")
    public void logDisplayMethod() {}

    @Around("logDisplayMethod()")
    public Object aroundLogDisplay(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.nanoTime();  // 시작 시간 (ns 단위)

        log.info("▶▶ 쿼리 실행 시작: {}", methodName);

        Object result;
        try {
            result = joinPoint.proceed(); // 메서드 실행
        } catch (Throwable ex) {
            log.error("❌ 예외 발생: {}, error={}", methodName, ex.getMessage(), ex);
            throw ex;
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;  // ms 단위로 변환
        log.info("◀◀ 쿼리 실행 종료: {} (소요시간: {} ms)", methodName, durationMs);

        // 제한 시간 초과 확인
        Long timeout = stopWatchProperty.getTimeout().get(methodName);
        if (timeout != null && durationMs > timeout) {
            log.warn("⚠️ 실행 시간 초과: {} > {} ms (허용 시간)", durationMs, timeout);
            // 필요 시 알림, 모니터링 연동, 예외 throw 등 가능
        }

        return result;
    }
}


////////////////////////////////////////////////////////////////////////////////////////
package com.example.aspect;

import com.example.config.StopWatchProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class QueryServiceAspect {

    private final StopWatchProperty stopWatchProperty;

    @Pointcut("@annotation(com.example.aspect.logDisplay)")
    public void logDisplayMethod() {}

    @Around("logDisplayMethod()")
    public Object aroundLogDisplay(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        String methodName = joinPoint.getSignature().getName();

        log.info("▶▶ 쿼리 실행 시작: {}", methodName);

        Object result;

        try {
            stopWatch.start();
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            log.error("❌ 쿼리 실행 중 예외 발생: {}, error={}", methodName, ex.getMessage(), ex);
            throw ex;
        } finally {
            stopWatch.stop();
            long duration = stopWatch.getTotalTimeMillis();
            log.info("◀◀ 쿼리 실행 종료: {} (소요시간: {} ms)", methodName, duration);

            Long timeout = stopWatchProperty.getTimeout().get(methodName);
            if (timeout != null && duration > timeout) {
                log.warn("⚠ 쿼리 실행 시간 초과: {} > {} ms (허용)", duration, timeout);
                // 필요시 알림 또는 예외 throw 가능
            }
        }

        return result;
    }
}

////////////////////////////////////////////////////////////////////////////////////////
package com.example.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Aspect
@Component
public class QueryServiceAspect {

    /**
     * @brief logDisplay 어노테이션이 붙은 메서드를 AOP 대상 포인트컷으로 지정
     */
    @Pointcut("@annotation(com.example.aspect.logDisplay)")
    public void logDisplayMethod() {}

    /**
     * @brief 쿼리 실행 시간 측정용 AOP
     * @param joinPoint 대상 메서드
     * @return 실제 메서드 실행 결과
     * @throws Throwable 예외 발생 시 그대로 전달
     * @history 2025.07.08 v1.0 최초 작성
     */
    @Around("logDisplayMethod()")
    public Object aroundLogDisplay(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();  // 요청마다 새 인스턴스 생성
        String methodName = joinPoint.getSignature().toShortString();

        log.info("▶▶ 쿼리 실행 시작: {}", methodName);

        Object result;

        try {
            stopWatch.start();  // 안전하게 시작
            result = joinPoint.proceed();  // 실제 쿼리 실행
        } catch (Throwable ex) {
            log.error("❌ 쿼리 실행 중 예외 발생: {}, error={}", methodName, ex.getMessage(), ex);
            throw ex;  // 예외는 그대로 전달 (트랜잭션 영향 없음)
        } finally {
            stopWatch.stop();
            log.info("◀◀ 쿼리 실행 종료: {} (소요시간: {} ms)", methodName, stopWatch.getTotalTimeMillis());
        }

        return result;
    }
}


