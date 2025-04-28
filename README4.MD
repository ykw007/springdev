좋습니다!  
요청하신 흐름은 약간 복합적이지만, 실제 **Kafka + DB 조회 + 가공 + 저장** 프로세스에서 아주 많이 쓰이는 패턴입니다.  

정확한 요청을 다시 요약하면:

---
# 🌟 요청 시나리오 요약

1. Kafka 메시지를 수신 → **VO1** 에 담음  
2. 이 VO1의 값으로 DB에서 JOIN 쿼리 조회 → **VO2**로 결과 받음  
3. VO1 + VO2를 조합해서 추가 데이터 생성 → **DTO**로 조립  
4. DTO를 **Entity**로 변환해서 최종적으로 **DB 저장**

---
# 📦 전체 플로우

```plaintext
Kafka 메시지 수신
↓
VO1 생성 (수신 메시지 VO)
↓
DB JOIN 조회
↓
VO2 생성 (조인 결과 VO)
↓
VO1 + VO2 합쳐서 DTO 생성
↓
DTO → Entity 변환
↓
DB 저장
```

---

# 🔥 예제 설계

### 📄 1. Kafka 수신 메시지 (JSON)

```json
{
  "userId": 10,
  "productId": 200
}
```

- userId와 productId를 받는다.
- 여기서 userId를 기반으로 추가 정보를 조회할 것이다.

---

### 📄 2. VO1: Kafka 수신용 (`PurchaseRequestVo.java`)

```java
package com.example.vo;

import lombok.Getter;

@Getter
public class PurchaseRequestVo {
    private final Long userId;
    private final Long productId;

    public PurchaseRequestVo(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }
}
```

✅ Kafka 수신 메시지를 담는 VO입니다.

---

### 📄 3. DB JOIN 조회 쿼리 결과 VO2 (`UserInfoVo.java`)

```java
package com.example.vo;

import lombok.Getter;

@Getter
public class UserInfoVo {
    private final String username;
    private final String membershipLevel;

    public UserInfoVo(String username, String membershipLevel) {
        this.username = username;
        this.membershipLevel = membershipLevel;
    }
}
```

✅ userId로 users 테이블을 조회한 결과입니다.

---

### 📄 4. DTO: 가공 + 추가 변수 (`PurchaseDto.java`)

```java
package com.example.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PurchaseDto {
    private final Long userId;
    private final Long productId;
    private final String username;
    private final String membershipLevel;
    private final String purchaseStatus; // 💥 추가된 비즈니스 변수
}
```
✅ DB 조회 결과와 Kafka 수신 결과를 합친 + 추가 데이터(purchaseStatus)까지 담는 DTO입니다.

---

### 📄 5. Entity: 최종 DB 저장용 (`PurchaseEntity.java`)

```java
package com.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "purchases")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long productId;
    private String username;
    private String membershipLevel;
    private String purchaseStatus;
}
```

✅ DB에 저장될 최종 Entity입니다.

---

# 🛠️ Mapper (MapStruct 사용)

**PurchaseMapper.java**

```java
package com.example.mapper;

import com.example.dto.PurchaseDto;
import com.example.entity.PurchaseEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface PurchaseMapper {

    PurchaseMapper INSTANCE = Mappers.getMapper(PurchaseMapper.class);

    PurchaseEntity toEntity(PurchaseDto dto);
}
```
✅ DTO를 Entity로 변환하는 Mapper입니다.

---

# 🔥 Kafka Listener

**PurchaseKafkaListener.java**

```java
package com.example.kafka;

import com.example.dto.PurchaseDto;
import com.example.entity.PurchaseEntity;
import com.example.mapper.PurchaseMapper;
import com.example.repository.PurchaseRepository;
import com.example.service.UserInfoService;
import com.example.vo.PurchaseRequestVo;
import com.example.vo.UserInfoVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseKafkaListener {

    private final ObjectMapper objectMapper;
    private final UserInfoService userInfoService;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseMapper purchaseMapper;

    @KafkaListener(topics = "purchase.request", groupId = "purchase-group")
    public void listen(String message) {
        try {
            // 1. Kafka 메시지 수신 → VO1 생성
            PurchaseRequestVo requestVo = objectMapper.readValue(message, PurchaseRequestVo.class);

            // 2. userId로 DB 조회 → VO2 생성
            UserInfoVo userInfoVo = userInfoService.findUserInfoByUserId(requestVo.getUserId());

            // 3. VO1 + VO2 + 추가 데이터로 DTO 생성
            PurchaseDto purchaseDto = PurchaseDto.builder()
                    .userId(requestVo.getUserId())
                    .productId(requestVo.getProductId())
                    .username(userInfoVo.getUsername())
                    .membershipLevel(userInfoVo.getMembershipLevel())
                    .purchaseStatus("REQUESTED") // 추가 상태
                    .build();

            // 4. DTO → Entity 변환
            PurchaseEntity entity = purchaseMapper.toEntity(purchaseDto);

            // 5. DB 저장
            purchaseRepository.save(entity);

            log.info("Purchase saved: {}", entity.getId());

        } catch (Exception e) {
            log.error("Error processing purchase request: {}", message, e);
        }
    }
}
```

---

# 📚 UserInfoService (DB 조회용)

```java
package com.example.service;

import com.example.mapper.UserInfoQueryMapper;
import com.example.vo.UserInfoVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserInfoService {

    private final UserInfoQueryMapper userInfoQueryMapper;

    public UserInfoVo findUserInfoByUserId(Long userId) {
        return userInfoQueryMapper.findByUserId(userId);
    }
}
```

---

# 📄 MyBatis Mapper (DB 조회용)

**UserInfoQueryMapper.java**

```java
@Mapper
public interface UserInfoQueryMapper {
    UserInfoVo findByUserId(Long userId);
}
```

**UserInfoQueryMapper.xml**

```xml
<mapper namespace="com.example.mapper.UserInfoQueryMapper">

    <select id="findByUserId" resultType="com.example.vo.UserInfoVo">
        SELECT 
            u.username,
            m.level AS membershipLevel
        FROM users u
        JOIN memberships m ON u.id = m.user_id
        WHERE u.id = #{userId}
    </select>

</mapper>
```

---

# ✅ 최종 전체 흐름 요약

```plaintext
1. Kafka 메시지 수신 → VO1 생성 (PurchaseRequestVo)
2. DB JOIN 조회 → VO2 생성 (UserInfoVo)
3. VO1 + VO2 + 추가 데이터 → DTO 생성 (PurchaseDto)
4. DTO → Entity 변환 (PurchaseEntity)
5. DB 저장
```

---

# ✨ 핵심 정리

| 구분 | 목적 |
|:---|:---|
| **VO1** | Kafka 수신 데이터 담기 |
| **VO2** | DB 조회 결과 담기 |
| **DTO** | VO1+VO2 합쳐서 가공 및 추가 데이터 세팅 |
| **Entity** | 최종 DB 저장용 객체 |
| **Mapper** | VO→DTO, DTO→Entity 변환 자동화 |

---

# 🔥 최종 한 줄 요약

> **Kafka 수신 데이터 + DB 조회 데이터 → DTO로 가공 → Entity 변환 → DB 저장.**  
>  
> 이렇게 계층별로 나누면 코드가 깔끔하고 확장성, 유지보수성이 뛰어납니다.

---

# ➡️ 추가로 갈 수 있는 고급 주제

- Kafka Listener에서 수신 실패 시 Retry/Dead Letter 처리하기
- MapStruct에서 복합 VO → DTO 변환(다중 Source 매핑) 처리하기
- Kafka 수신 데이터에 따라 동적으로 다른 DB 조회하는 방법

---
=======================================================================================================================

좋습니다! 🔥  
이번에는 약속대로 **여러 VO를 합쳐서 하나의 DTO로 변환하는 방법**,  
즉 **복합 VO → DTO 매핑 (다중 Source 매핑)**을  
**MapStruct 고급 기능**으로 깔끔하게 보여드릴게요!

---

# 🌟 목표
- Kafka로 받은 **VO1**  
- DB 조회해서 얻은 **VO2**
- 이 둘을 **하나의 DTO**로 합쳐서 만든다.
- 이 과정을 **MapStruct**를 이용해서 자동화한다.

---

# 📚 예제 설정

### 수신 VO1 (`PurchaseRequestVo`)
```java
@Getter
@AllArgsConstructor
public class PurchaseRequestVo {
    private final Long userId;
    private final Long productId;
}
```
- Kafka 메시지에서 받음

---

### 조회 VO2 (`UserInfoVo`)
```java
@Getter
@AllArgsConstructor
public class UserInfoVo {
    private final String username;
    private final String membershipLevel;
}
```
- userId로 DB 조회해서 얻음

---

### 최종 만들 DTO (`PurchaseDto`)
```java
@Getter
@Builder
public class PurchaseDto {
    private final Long userId;
    private final Long productId;
    private final String username;
    private final String membershipLevel;
    private final String purchaseStatus; // 추가 가공 필드
}
```

---
# 🔥 여기까지 흐름 요약

```plaintext
VO1 (PurchaseRequestVo) + VO2 (UserInfoVo)
↓
PurchaseDto
```

---

# 1. MapStruct 복합 매핑 Mapper 만들기

**PurchaseObjectMapper.java**

```java
package com.example.mapper;

import com.example.dto.PurchaseDto;
import com.example.vo.PurchaseRequestVo;
import com.example.vo.UserInfoVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface PurchaseObjectMapper {

    PurchaseObjectMapper INSTANCE = Mappers.getMapper(PurchaseObjectMapper.class);

    @Mapping(source = "purchaseRequestVo.userId", target = "userId")
    @Mapping(source = "purchaseRequestVo.productId", target = "productId")
    @Mapping(source = "userInfoVo.username", target = "username")
    @Mapping(source = "userInfoVo.membershipLevel", target = "membershipLevel")
    // purchaseStatus는 나중에 별도로 세팅 (builder pattern)
    PurchaseDto toDto(PurchaseRequestVo purchaseRequestVo, UserInfoVo userInfoVo);
}
```

✅ 포인트는:
```java
toDto(PurchaseRequestVo purchaseRequestVo, UserInfoVo userInfoVo);
```
**→ 매개변수 2개를 동시에 받아서 하나의 DTO를 생성합니다!**

그리고 각각 어떤 필드에서 어떤 DTO 필드로 매핑할지 `@Mapping`으로 명확히 지정해줍니다.

---

# 2. Service 코드에서 사용 예시

```java
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseObjectMapper purchaseObjectMapper;

    public PurchaseDto buildPurchaseDto(PurchaseRequestVo requestVo, UserInfoVo userInfoVo) {
        PurchaseDto dto = purchaseObjectMapper.toDto(requestVo, userInfoVo);

        // 추가 비즈니스 필드 세팅
        return PurchaseDto.builder()
                .userId(dto.getUserId())
                .productId(dto.getProductId())
                .username(dto.getUsername())
                .membershipLevel(dto.getMembershipLevel())
                .purchaseStatus("REQUESTED") // 추가 가공
                .build();
    }
}
```

✅ MapStruct로 기본 필드 변환하고, 추가 필드는 Builder를 이용해 채워줍니다.

---

# 🎯 요약

| 항목 | 설명 |
|:---|:---|
| **복합 매핑** | 하나의 Mapper 메서드에 여러 source 객체를 넘겨준다. |
| **@Mapping으로 필드 연결** | source → target 명확히 지정 |
| **추가 가공 필드** | MapStruct 외부에서 별도로 Builder나 Setter로 추가 |

---

# 📦 한 장 그림으로

```plaintext
PurchaseRequestVo (Kafka) +
UserInfoVo (DB 조회)
↓ (MapStruct)
PurchaseDto (비즈니스 DTO)
↓ (Builder로 추가 필드 보완)
↓
Entity 변환 → DB 저장
```

---

# ✅ 최종 정리 문장

> **MapStruct는 여러 VO를 동시에 받아 하나의 DTO로 변환할 수 있고,  
> 필요한 추가 필드는 Builder나 수동 세팅으로 보완할 수 있다.**

---

============================================================================================================

좋습니다! 🔥🔥  
이번에는 약속대로 **복합 VO 매핑하면서 중간에 데이터 가공하는 고급 MapStruct 예제**를 완전 실전처럼 만들어 보여드릴게요.

---

# 🌟 목표
- 여러 VO (Kafka 수신 + DB 조회 결과)를 MapStruct로 조합
- 매핑 과정에서 **문자열 합치기**, **가공**, **포맷팅** 같은 추가 로직도 넣기
- Builder 없이 **MapStruct 내부에서 다 처리하기**

---

# 📚 예제 설정

**수신 데이터 1: PurchaseRequestVo (Kafka)**

```java
@Getter
@AllArgsConstructor
public class PurchaseRequestVo {
    private final Long userId;
    private final Long productId;
}
```

---

**조회 데이터 2: UserInfoVo (DB 조회)**

```java
@Getter
@AllArgsConstructor
public class UserInfoVo {
    private final String username;
    private final String membershipLevel;
}
```

---

**최종 목적지 DTO: PurchaseDto**

```java
@Getter
@Builder
public class PurchaseDto {
    private final Long userId;
    private final Long productId;
    private final String userInfoSummary; // 💥 username + (membershipLevel) 합쳐서 표현
    private final String purchaseStatus;  // 💥 기본값 "REQUESTED"
}
```

---

# 🛠️ MapStruct Mapper 설계

**PurchaseObjectMapper.java**

```java
package com.example.mapper;

import com.example.dto.PurchaseDto;
import com.example.vo.PurchaseRequestVo;
import com.example.vo.UserInfoVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface PurchaseObjectMapper {

    PurchaseObjectMapper INSTANCE = Mappers.getMapper(PurchaseObjectMapper.class);

    @Mappings({
        @Mapping(source = "purchaseRequestVo.userId", target = "userId"),
        @Mapping(source = "purchaseRequestVo.productId", target = "productId"),
        @Mapping(target = "userInfoSummary", expression = "java(buildUserInfoSummary(userInfoVo))"), // 💥 사용자 정보 요약 문자열
        @Mapping(target = "purchaseStatus", constant = "REQUESTED") // 💥 고정값 세팅
    })
    PurchaseDto toDto(PurchaseRequestVo purchaseRequestVo, UserInfoVo userInfoVo);

    // 💬 문자열 합치기 가공 로직
    default String buildUserInfoSummary(UserInfoVo userInfoVo) {
        if (userInfoVo == null) {
            return "Unknown User";
        }
        return userInfoVo.getUsername() + " (" + userInfoVo.getMembershipLevel() + ")";
    }
}
```

✅ 여기서 **핵심 포인트**
- `expression = "java(메소드호출)"`  
- `constant = "문자열"`  
- `default 메소드`를 통해 필요한 로직을 따로 뺄 수 있음

---

# 🔥 흐름 요약

```plaintext
PurchaseRequestVo (Kafka)
+
UserInfoVo (DB 조회)

↓ (MapStruct 매핑 중)
- userInfoSummary = username + (membershipLevel)
- purchaseStatus = "REQUESTED" 고정

↓
PurchaseDto 완성
```

---

# 🎯 실제 Service 사용 예시

```java
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseObjectMapper purchaseObjectMapper;

    public PurchaseDto buildPurchaseDto(PurchaseRequestVo requestVo, UserInfoVo userInfoVo) {
        return purchaseObjectMapper.toDto(requestVo, userInfoVo);
    }
}
```

✅ Builder로 다시 손댈 필요 없이 한방에 DTO 완성!

---

# 📦 여기서 쓰인 MapStruct 기능 요약

| 기능 | 사용법 | 설명 |
|:---|:---|:---|
| **expression** | `expression = "java(메소드 호출)"` | 매핑 중 가공, 변환 작업을 직접 넣음 |
| **constant** | `constant = "값"` | DTO 필드에 고정값 세팅 |
| **default method** | `default String 메소드(...)` | 복잡한 가공 로직을 Mapper 안에 따로 뺌 |

---

# ✅ 정리 문장

> **MapStruct는 여러 VO를 합쳐서 하나의 DTO로 만들면서,  
> 중간에 문자열 가공, 값 변환, 기본값 세팅까지 깔끔하게 자동 처리할 수 있다.**

---

# ➡️ 이 다음 심화로 갈 수 있는 주제

- MapStruct `@Context`를 이용해서 매핑 중 외부 서비스 호출하기
- 컬렉션(List<VO>) → List<DTO> 변환 매핑하기
- 복합 매핑 + null-safe 처리 + defaultExpression 고급 통합 예제
