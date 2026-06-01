# point-service

무료 포인트 시스템 API 과제 구현 프로젝트입니다.

## 개발 환경
- Java 21
- Spring Boot 3.5.0
- Gradle Kotlin DSL
- Spring Web, Spring Data JPA, Spring AOP, Bean Validation
- H2 Database
- JUnit 5

## 실행 방법

```bash
./gradlew bootRun
```

기본 실행 후 H2 Console은 아래 경로에서 접근할 수 있습니다.

```text
http://localhost:8080/h2-console
```

H2 접속 정보는 다음과 같습니다.

```text
JDBC URL: jdbc:h2:mem:point
User Name: sa
Password:
```

## 테스트

전체 테스트 실행:

```bash
./gradlew test
```

개별 API 테스트는 API 단위 테스트 클래스로 분리되어 있습니다.

- `EarnApiTests`
- `EarnCancelApiTests`
- `UseApiTests`
- `UseCancelApiTests`
- `ExpireApiTests`
- `BalanceApiTests`
- `HistoryApiTests`
- `TransactionLookupApiTests`

## 산출물
- OpenAPI 문서: `docs/openapi.json`
- DB 스키마: `src/main/resources/schema.sql`
- ERD 이미지: `src/main/resources/erd.pdf`
- 논리 ERD: [`src/main/resources/logical_erd.md`](src/main/resources/logical_erd.md)
- AWS 운영 아키텍처 이미지: `src/main/resources/aws_architecture.pdf`
 
테이블 정의, 코멘트, 인덱스, 유니크 제약은 `schema.sql`에서 관리합니다.

## 패키지 구조
```text
com.payment.point
├── api
├── application
├── config
├── domain
│   ├── balance
│   ├── earn
│   ├── expire
│   ├── transaction
│   └── use
└── support
```
## 설정
주요 정책값은 `src/main/resources/application.yml`에서 변경할 수 있습니다.

```yaml
point:
  earn:
    min-amount: 1
    max-amount: 100000
    default-expire-period: P365D
    min-expire-period: P1D
    max-expire-period: P5Y
  member:
    max-balance-amount: 1000000
  lock:
    ttl: 10s
```

## 주요 API
| 기능           | Method | Path                                                  | 성공 상태         |
|--------------|--------|-------------------------------------------------------|---------------|
| 적립           | POST   | `/v1/members/{memberId}/points/earn`                  | `201 Created` |
| 적립취소         | POST   | `/v1/members/{memberId}/points/earn-cancel`           | `201 Created` |
| 사용           | POST   | `/v1/members/{memberId}/points/use`                   | `201 Created` |
| 사용취소         | POST   | `/v1/members/{memberId}/points/use-cancel`            | `201 Created` |
| 만료           | POST   | `/v1/members/{memberId}/points/expire`                | `200 OK`      |
| 잔액조회         | GET    | `/v1/members/{memberId}/points/balance`               | `200 OK`      |
| 이력조회         | GET    | `/v1/members/{memberId}/points/histories`             | `200 OK`      |
| 주문번호 기반 거래조회 | GET    | `/v1/members/{memberId}/points/transactions/by-order` | `200 OK`      |

- 과제 필수 기능은 적립, 적립취소, 사용, 사용취소입니다.
- 만료, 잔액조회, 이력조회, 주문번호 기반 거래조회는 운영 편의성과 장애 대응을 위해 확장 구현했습니다.

### 공통 요청 검증
- `memberId`는 외부 시스템에서 전달된 식별자로 보고 포맷을 제한하지 않습니다. 단, 공백 문자를 포함할 수 없으며 최대 32자까지 허용합니다.
- 거래 발생 API의 `orderNo`는 필수값이며 공백 문자열을 허용하지 않고 최대 40자까지 허용합니다.
- 금액 필드는 0보다 큰 정수만 허용합니다.
- DB의 `ORDER_NO` 컬럼은 향후 확장을 고려해 `VARCHAR(100)`으로 관리합니다.


## 과제 요구사항

### 적립
- 1회 적립 가능 포인트는 1포인트 이상 100,000포인트 이하입니다.
- 1회 최대 적립 가능 포인트는 `application.yml`의 `point.earn.max-amount`로 제어합니다.
- 개인별 최대 보유 가능 포인트는 `point.member.max-balance-amount`로 제어합니다.
- 적립 건은 `PNT_EARN_MST`에 원장으로 저장합니다.
- 적립 건별 사용 내역은 `PNT_USE_ALLOC`으로 1원 단위까지 추적할 수 있습니다.
- 관리자 수기 지급 포인트는 `EarnType.MANUAL`로 일반 적립과 구분합니다.
- 모든 적립 포인트는 만료일을 가집니다.
- 기본 만료 기간은 `P365D`, 최소 기간은 `P1D`, 최대 기간은 `P5Y` 미만입니다.
- 만료일은 `LocalDate`와 DB `DATE`로 관리하며, 만료일 당일 00시부터 사용할 수 없습니다.

### 적립취소
- 특정 적립 거래번호를 기준으로 적립취소를 처리합니다.
- 원 적립 금액 중 일부라도 사용된 경우 적립취소를 허용하지 않습니다.
- 현재 정책상 원 적립 건이 이미 만료된 경우에도 적립취소를 허용하지 않습니다.
- 적립취소 성공 시 잔액, 적립 원장, 거래 이력을 하나의 트랜잭션으로 갱신합니다.

### 사용
- 사용 요청은 주문번호를 필수로 받습니다.
- 사용 거래는 `PNT_USE_MST`에 저장하고, 어떤 적립 건에서 얼마를 차감했는지는 `PNT_USE_ALLOC`에 저장합니다.
- 사용 우선순위는 `MANUAL` 포인트 우선, 만료일 빠른 순, 생성일 빠른 순, 포인트 거래번호 빠른 순입니다.
- 잔액 부족 시 전체 요청을 실패 처리합니다.

### 사용취소
- 사용 거래번호를 기준으로 전체 또는 부분 사용취소를 처리합니다.
- 사용취소는 최초 사용 차감 순서인 `PRIORITY ASC` 순서로 복원합니다.
- 원 적립 건이 만료되지 않았다면 기존 적립 건의 잔여 금액을 복원합니다.
- 원 적립 건이 사용취소 시점에 이미 만료되었다면 `EarnType.RESTORE` 신규 적립을 생성합니다.
- `RESTORE` 신규 적립의 만료일은 사용취소 처리일에 기본 만료 기간(`point.earn.default-expire-period`, 기본값 `P365D`)을 더해 설정합니다.
- 사용취소 상세 이력은 `PNT_USE_CANCEL_HIST`에 저장하며, `ORIGINAL_RESTORE`와 `NEW_EARN`으로 복원 유형을 구분합니다.

## 추가 설계 및 제약조건

### 회원 정책
- 별도 회원 마스터 테이블은 두지 않습니다.
- `memberId`는 외부 시스템에서 전달된 식별자로 보고 포맷을 제한하지 않습니다. 단, 공백 문자를 포함할 수 없으며 최대 32자까지 허용합니다.
- 최초 회원 잔액 row는 적립 API에서만 생성합니다.
- 적립 외 금액 변경 API와 조회 API에서 잔액 row가 없으면 `INVALID_USER`를 반환합니다.
- 잔액 row가 존재하고 잔액이 0인 경우는 유효한 회원으로 보고 0 잔액을 응답합니다.
- 거래 이력 조회는 유효 회원의 조회 결과가 없으면 `NO_HISTORY_RESULT`를 반환합니다.
- 주문번호 기반 거래조회는 유효 회원의 주문번호와 거래 유형 조합이 없으면 `exists=false`를 반환합니다.

### 주문번호 중복 정책
- `orderNo`는 멱등성 및 중복 거래 방지를 위한 요청자 주문번호입니다.
- 동일 회원의 동일 `orderNo`, 동일 `txType` 조합은 중복 거래로 판단하며, `DUPLICATED_ORDER`를 반환하여 중복 반영을 방지합니다.
- 동일 회원과 동일 주문번호라도 거래 유형이 다르면 별도 거래로 처리할 수 있습니다.
- 네트워크 유실로 최초 응답을 받지 못한 경우에도 재시도 요청은 기존 요청과 동일한 `orderNo`를 사용해야 합니다.
- 네트워크 유실 상황에서는 `GET /v1/members/{memberId}/points/transactions/by-order` API로 처리 여부와 거래 결과를 조회할 수 있습니다.
- 주문번호 기반 거래조회는 `orderNo`와 `txType`을 필수값으로 받습니다. `orderNo`는 공백 문자열을 허용하지 않으며, 최대 40자까지 허용합니다.

### 거래번호 정책
- 포인트 거래번호 `PTXNO`는 26자리 숫자 문자열입니다.
- 생성 규칙은 DB 현재시각 `yyyyMMddHHmmssSSS` 17자리와 DB sequence 9자리 조합입니다.
- 서버 간 clock skew 영향을 줄이기 위해 애플리케이션 시간이 아니라 DB 현재시각을 기준으로 생성합니다.
- 거래번호는 `POINT.SEQ_PTXNO`, 내부 상세 ID는 `POINT.SEQ_DETAIL_ID`를 사용합니다.
- DB와 엔티티 내부에서는 `PTXNO`, `OPTXNO`를 사용하고, 외부 API에서는 `pointTransactionNo`, `originalPointTransactionNo`로 노출합니다.

### 트랜잭션 정책
- 금액을 변경하는 API는 Facade 계층에서 단일 DB 트랜잭션으로 처리합니다.
- 거래 이력 `PNT_TR_HIST`는 Append-Only 정책입니다.
- 실패 시 잔액, 원장, 상세 이력, 거래 이력 변경을 모두 롤백합니다.
- 비즈니스 상태 변경은 JPA 엔티티의 도메인 메서드와 변경 감지를 사용합니다.
- 엔티티 연관관계는 최소화하고 원장 간 연결은 거래번호 ID로 관리합니다.
- 잔액 row의 `VERSION`은 JPA 낙관적 락을 위한 보조 안전장치입니다.

### FK 정책
- 원장 간 필수 참조 관계에는 FK를 적용합니다.
- `PNT_USE_ALLOC.PTXNO`는 `PNT_USE_MST.PTXNO`를 참조합니다.
- `PNT_USE_ALLOC.EARN_PTXNO`는 `PNT_EARN_MST.PTXNO`를 참조합니다.
- `PNT_USE_CANCEL_HIST.USE_PTXNO`는 `PNT_USE_MST.PTXNO`를 참조합니다.
- `PNT_USE_CANCEL_HIST.USE_ALLOC_ID`는 `PNT_USE_ALLOC.USE_ALLOC_ID`를 참조합니다.
- `PNT_USE_CANCEL_HIST.RESTORE_PTXNO`는 신규 RESTORE 적립이 생성된 경우 `PNT_EARN_MST.PTXNO`를 참조합니다.
- 삭제 정책은 기본값인 `RESTRICT`를 유지합니다. 자식 row가 참조 중인 부모 원장은 단독 삭제할 수 없으며, `ON DELETE CASCADE`를 사용하지 않습니다.

### 동시성 제어
- 포인트 금액 변경 API는 `memberId` 기준 요청 락을 적용합니다.
- 현재 락은 Caffeine 로컬 캐시 기반입니다.
- 동일 회원의 금액 변경 요청이 이미 처리 중이면 후속 요청은 대기하지 않고 실패합니다.
- 로컬 락 TTL 기본값은 `10s`입니다.
- 락 해제 시 요청별 token이 일치할 때만 삭제하여, 만료 후 후속 요청이 획득한 락을 이전 요청이 해제하지 않도록 합니다.
- 현재 구현은 단일 애플리케이션 인스턴스 실행을 전제로 합니다.
- 다중 인스턴스 운영 시 Redis 또는 DB 기반 분산 락으로 전환해야 합니다.
- 요청 처리가 TTL을 초과하면 동일 회원의 후속 요청이 진입할 수 있으므로 운영 전 timeout 정책 또는 락 구현 보완이 필요합니다.

### 만료 처리
- 만료 API는 회원 단위로 처리합니다.
- 만료도 잔액 변경이 발생하므로 `memberId` 기준 요청 락을 적용합니다.
- 요청 기준일까지 만료된 해당 회원의 적립 원장만 처리합니다.
- 요청 기준일은 서버 현재일 이하만 허용하며, 미래 날짜 요청은 `INVALID_PARAMETER`를 반환합니다.
- 만료 처리 시 사용 가능한 잔액을 차감하고 누적 만료 금액을 증가시킵니다.
- 운영 환경에서는 외부 배치가 매일 00시에 만료 API를 호출하는 방식을 전제로 합니다.
- 배치 처리 전에 사용 요청이 유입되는 경계 상황은 회원별 다음 만료 예정일 Snapshot으로 보완합니다.

### 다음 만료 예정일 Snapshot
- `PNT_MEMBER_BAL.NEXT_EXP_DT`에는 사용 가능한 적립 원장 중 가장 빠른 만료일을 저장합니다.
- 적립 시 신규 적립 만료일이 현재 값보다 빠르면 갱신합니다.
- 적립취소 시 취소 원장의 만료일이 현재 값과 같으면 남은 적립 원장을 조회해 재계산합니다.
- 사용 시 `NEXT_EXP_DT <= 현재일`인 회원만 만료 원장을 선처리합니다.
- 사용 원장 차감 후에는 남은 적립 원장을 기준으로 다음 만료 예정일을 재계산합니다.
- 사용취소 시 복원된 적립 원장의 만료일이 현재 값보다 빠르면 갱신합니다.
- 만료 API 처리 후에는 남은 적립 원장을 기준으로 다음 만료 예정일을 재계산합니다.

### 사용 처리 순서
```text
1. 회원 요청 락 획득
2. 금액 및 주문번호 검증
3. 회원 잔액 조회
4. NEXT_EXP_DT <= 현재일이면 회원별 만료 선처리
5. 회원 잔액을 MANUAL 우선으로 차감
6. 사용 원장 생성
7. 적립 원장을 우선순위대로 차감하고 Allocation 생성
8. NEXT_EXP_DT 재계산
9. 사용 거래 이력 생성
10. 트랜잭션 commit 후 회원 요청 락 해제
```

사용 원장은 Allocation FK의 부모 row이므로 Allocation보다 먼저 저장합니다.

### 사용취소 처리 순서
```text
1. 회원 요청 락 획득
2. 금액 및 주문번호 검증
3. 회원 잔액과 원 사용 원장 조회
4. 취소 가능한 Allocation을 PRIORITY ASC 순서로 조회
5. Allocation에 연결된 원 적립 원장을 일괄 조회
6. 원 적립 건이 만료 전이면 기존 적립 원장 복원
7. 원 적립 건이 만료 후이면 신규 RESTORE 적립 생성
8. Allocation 취소 금액과 사용취소 상세 이력 저장
9. NEXT_EXP_DT 갱신 및 사용 원장 취소 처리
10. 사용취소 거래 이력 생성
11. 트랜잭션 commit 후 회원 요청 락 해제
```

### 이력 조회
- 이력 조회는 `startDate`, `endDate`를 필수로 받습니다.
- 날짜 형식은 `yyyy-MM-dd`입니다.
- 조회 기간은 최대 3개월입니다.
- `startDate`는 `endDate`보다 이전이어야 합니다.
- `txType`은 선택값이며, 없으면 전체 거래 유형을 조회합니다.
- 조회 결과가 없으면 `NO_HISTORY_RESULT`를 반환합니다.

## 데이터 모델(상세 정보 schema.sql 참고)
| 테이블                   | 역할                                        |
|-----------------------|-------------------------------------------|
| `PNT_MEMBER_BAL`      | 회원별 현재 잔액, 누적 만료 금액, 다음 만료 예정일 Snapshot   |
| `PNT_TR_HIST`         | 적립, 적립취소, 사용, 사용취소, 만료 거래 이력. Append-Only |
| `PNT_EARN_MST`        | 적립 건별 금액, 상태, 만료일 원장                      |
| `PNT_USE_MST`         | 주문별 사용 금액과 취소 가능 잔액 원장                    |
| `PNT_USE_ALLOC`       | 사용 거래가 어떤 적립 원장에서 차감되었는지 기록하는 Allocation  |
| `PNT_USE_CANCEL_HIST` | 사용취소 시 원 적립 복원 또는 신규 RESTORE 적립 결과 이력     |

### 금액 불변식
```text
PNT_MEMBER_BAL.TOTAL_AMT
= NORMAL_AMT + MANUAL_AMT

PNT_EARN_MST.RMN_AMT
= EARN_AMT - USE_AMT - EARN_CNCL_AMT - EXPIRED_AMT

PNT_USE_MST.RMN_AMT
= USE_AMT - CNCL_AMT

PNT_USE_ALLOC.RMN_AMT
= CNSM_AMT - CNCL_AMT
```

### 상태값
| 구분                       | 값                                                |
|--------------------------|--------------------------------------------------|
| 거래 유형 `TxType`           | `EARN`, `EARN_CNCL`, `USE`, `USE_CNCL`, `EXPIRE` |
| 적립 유형 `EarnType`         | `NORMAL`, `MANUAL`, `RESTORE`                    |
| 적립 원장 상태 `EarnStatus`    | `ACTIVE`, `USED_UP`, `CNCL`, `EXPIRED`           |
| 사용 원장 상태 `UseStatus`     | `ACTIVE`, `PARTIAL_CNCL`, `CNCL`                 |
| 사용취소 복원 유형 `RestoreType` | `ORIGINAL_RESTORE`, `NEW_EARN`                   |

## 에러 응답
비즈니스 예외는 공통 에러 응답으로 반환합니다.

| ErrorCode                 | 메시지                             |
|---------------------------|---------------------------------|
| `INVALID_PARAMETER`       | 요청 파라미터가 올바르지 않습니다.             |
| `INVALID_USER`            | 유효하지 않은 회원입니다.                  |
| `NOT_ENOUGH_POINT`        | 포인트 잔액이 부족합니다.                  |
| `INCORRECT_POINT`         | 포인트 금액이 올바르지 않습니다.              |
| `ALREADY_CANCELED`        | 이미 취소된 거래입니다.                   |
| `NO_POINT_HISTORY`        | 포인트 거래 이력을 찾을 수 없습니다.           |
| `PARTIAL_CANCEL_FAIL`     | 부분취소를 처리할 수 없습니다.               |
| `EXPIRED_POINT`           | 만료된 포인트는 취소할 수 없습니다.            |
| `TIMEOUT`                 | 요청 시간이 초과되었습니다.                 |
| `SYSTEM_ERROR`            | 시스템 오류가 발생했습니다.                 |
| `DUPLICATED_ORDER`        | 이미 처리된 주문번호입니다.                 |
| `NO_REMAIN_POINT`         | 취소 가능한 잔여 포인트가 없습니다.            |
| `CANCEL_AMOUNT_EXCEEDED`  | 취소 요청 금액이 취소 가능한 잔여 금액을 초과했습니다. |
| `INVALID_HISTORY_PERIOD`  | 시작일은 종료일보다 이전이어야 합니다.           |
| `HISTORY_PERIOD_EXCEEDED` | 조회 가능 기간이 아닙니다.                 |
| `NO_HISTORY_RESULT`       | 조회 결과가 없습니다.                    |
| `POINT_PROCESSING`        | 이미 동일 사용자 거래 건이 처리 중입니다.          |

## 향후 확장 대응
- 만료 연장 기능을 대비하여 `PNT_EARN_MST` 내 `FIRST_EXP_DT`를 구성하여 최초 만료일과 만료일을 확인할 수 있도록 했습니다.
- 운영자에 의한 강제 만료 기능을 구현할 경우, 일반 만료 API와 분리된 권한 검증 및 강제 만료 사유 기록 기능을 추가해야 합니다.
