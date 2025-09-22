알겠습니다 🙂 지금 이 자리에서 **Kafka 메시지 수신 → DB 저장 / 외부 송신 모듈** 전용 사용자·운영자 매뉴얼 초안 내용을 정리해드릴게요. 따로 파일 다운로드하지 않고 텍스트 그대로 확인하실 수 있습니다.

---

# 📘 사용자 매뉴얼 (User Guide)

## 1. 개요

* 이 모듈은 **Kafka 메시지를 수신**하여 내부 DB에 저장하거나 외부 시스템으로 송신하는 백엔드 서비스입니다.
* 대상: **내부 운영팀, 관리자** (최종 사용자가 직접 UI를 다루지 않음)

---

## 2. 시작하기

* **접속 방법**: 별도의 화면(UI) 없음
* **상태 확인**:

  * REST API → `/actuator/health` 호출
  * 로그 모니터링(Grafana/CloudWatch/서버 로그)
* **필요 권한**: 서버 접근(SSH), 로그 열람 권한

---

## 3. 주요 절차

1. **메시지 정상 처리 확인**

   * DB `processed_messages` 테이블에 레코드 생성 여부 확인
2. **외부 송신 확인**

   * 외부 시스템 로그/응답 코드(200 OK 등) 확인
3. **오류 발생 시**

   * 로그 키워드: `ERROR`, `KafkaConsumer`, `DBInsertFail`, `SendFail`

---

## 4. FAQ

* **Q. 메시지가 누락된 것 같아요.**
  → Dead Letter Queue(DLQ) 또는 `error_messages` 테이블 확인

* **Q. DB에는 저장되었는데 외부 송신이 실패했어요.**
  → 재시도 큐 확인 후 운영 담당자에게 문의

---

# ⚙️ 운영자 매뉴얼 (Runbook)

## 1. 아키텍처 개요

* **Kafka Consumer**: 메시지 수신 및 처리
* **DB**: PostgreSQL/MySQL 등 (메시지 영속화)
* **외부 송신 모듈**: REST API 또는 Kafka Producer

---

## 2. 환경 설정

* **Profile**: `dev`, `stage`, `prod`
* **주요 환경변수**

  * `KAFKA_BOOTSTRAP_SERVERS = kafka:9092`
  * `DB_URL`, `DB_USER`, `DB_PASS`
  * `TARGET_ENDPOINT = https://api.example.com`

---

## 3. 배포 절차

1. Git 태그 생성 → CI 빌드 (Gradle/Maven)
2. Docker 이미지 빌드 및 레지스트리에 푸시
3. Kubernetes RollingUpdate 배포

   * 헬스체크: `/actuator/health`
4. Kafka 메시지 송신 테스트 후 DB/외부 송신 정상 여부 확인

---

## 4. 모니터링

* **Kafka Lag**: Consumer Lag 지표 확인
* **DB 상태**: Insert 실패율 모니터링
* **외부 송신**: 성공률(HTTP 200 비율) 모니터링

---

## 5. 장애 대응

* **Kafka 메시지 소비 지연** → 컨슈머 수 확장(HPA 스케일아웃)
* **DB 연결 풀 초과** → HikariCP `maxPoolSize` 조정
* **외부 송신 실패율 증가** → 대상 시스템 상태 점검 → 재시도 큐 확인

---

## 6. 백업 및 로그 관리

* **DB 백업**: 일/주 단위, 암호화 저장
* **Kafka DLQ 메시지**: 보존 및 재처리 절차 문서화
* **로그 보존**: 애플리케이션 로그(JSON) 30일 이상 유지

---

👉 이렇게 하면 “사용자 = 모니터링/확인 위주”, “운영자 = 배포·장애 대응·모니터링”이라는 역할 분담이 명확하게 됩니다.

원하시면 제가 여기에 **DB 테이블 예시 구조**(예: `processed_messages`, `error_messages`)와 **에러코드 표**까지 추가해드릴까요?
