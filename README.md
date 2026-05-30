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

API 테스트 Suite 실행:

```bash
./gradlew test --tests com.payment.point.api.PointApiTestSuite
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

## 문서

- 설계 문서: `docs/point_system_design.md`
- OpenAPI 문서: `docs/openapi.json`
- 문제 원문: `docs/problem.md`
- DB 스키마: `src/main/resources/schema.sql`

ERD 이미지는 별도 산출물로 정리 예정입니다. 현재 테이블 정의와 인덱스는 `schema.sql`과 설계 문서에 반영되어 있습니다.

## 주요 API

| 기능 | Method | Path |
|---|---|---|
| 적립 | POST | `/v1/members/{memberId}/points/earn` |
| 적립취소 | POST | `/v1/members/{memberId}/points/earn-cancel` |
| 사용 | POST | `/v1/members/{memberId}/points/use` |
| 사용취소 | POST | `/v1/members/{memberId}/points/use-cancel` |
| 만료 | POST | `/v1/members/{memberId}/points/expire` |
| 잔액조회 | GET | `/v1/members/{memberId}/points/balance` |
| 이력조회 | GET | `/v1/members/{memberId}/points/histories` |
| 주문번호 기반 거래조회 | GET | `/v1/members/{memberId}/points/transactions/by-order` |

과제 필수 기능은 적립, 적립취소, 사용, 사용취소입니다. 만료, 잔액조회, 이력조회, 주문번호 기반 거래조회는 운영 편의성과 장애 대응을 위해 확장 구현했습니다.

## 과제 요구사항 대응

### 적립

- 1회 적립 가능 포인트는 1포인트 이상 100,000포인트 이하입니다.
- 1회 최대 적립 가능 포인트는 `application.yml`의 `point.earn.max-amount`로 제어합니다.
- 개인별 최대 보유 가능 포인트는 `point.member.max-balance-amount`로 제어합니다.
- 적립 건은 `PNT_EARN_MST`에 원장으로 저장합니다.
- 적립 건별 사용 내역은 `PNT_USE_ALLOC`으로 1원 단위까지 추적할 수 있습니다.
- 관리자 수기 지급 포인트는 `EarnType.MANUAL`로 일반 적립과 구분합니다.
- 모든 적립 포인트는 만료일을 가집니다.
- 기본 만료 기간은 `P365D`, 최소 기간은 `P1D`, 최대 기간은 `P5Y` 미만입니다.

### 적립취소

- 특정 적립 거래번호를 기준으로 적립취소를 처리합니다.
- 원 적립 금액 중 일부라도 사용된 경우 적립취소를 허용하지 않습니다.
- 현재 정책상 원 적립 건이 이미 만료된 경우에도 적립취소를 허용하지 않습니다.
- 적립취소 성공 시 잔액, 적립 원장, 거래 이력을 하나의 트랜잭션으로 갱신합니다.

### 사용

- 사용 요청은 주문번호를 필수로 받습니다.
- 사용 거래는 `PNT_USE_MST`에 저장하고, 어떤 적립 건에서 얼마를 차감했는지는 `PNT_USE_ALLOC`에 저장합니다.
- 사용 우선순위는 `MANUAL` 포인트 우선, 만료일 빠른 순, 생성일 빠른 순입니다.
- 잔액 부족 시 전체 요청을 실패 처리합니다.

### 사용취소

- 사용 거래번호를 기준으로 전체 또는 부분 사용취소를 처리합니다.
- 사용취소는 최초 사용 차감 순서인 `PRIORITY ASC` 순서로 복원합니다.
- 원 적립 건이 만료되지 않았다면 기존 적립 건의 잔여 금액을 복원합니다.
- 원 적립 건이 사용취소 시점에 이미 만료되었다면 `EarnType.RESTORE` 신규 적립을 생성합니다.
- 사용취소 상세 이력은 `PNT_USE_CANCEL_HIST`에 저장하며, `ORIGINAL_RESTORE`와 `NEW_EARN`으로 복원 유형을 구분합니다.

## 추가 설계 및 제약조건

### 회원 정책

- 별도 회원 마스터 테이블은 두지 않습니다.
- `memberId`는 UUID에서 하이픈을 제거한 32자리 문자열을 기준으로 설계했습니다.
- 최초 회원 잔액 row는 적립 API에서만 생성합니다.
- 적립 외 금액 변경 API와 조회 API에서 잔액 row가 없으면 `INVALID_USER`를 반환합니다.
- 잔액 row가 존재하고 잔액이 0인 경우는 유효한 회원으로 보고 0 잔액을 응답합니다.

### 주문번호 중복 정책

- `orderNo`는 멱등성 및 중복 거래 방지를 위한 요청자 주문번호입니다.
- 동일 회원의 동일 주문번호는 중복 처리하지 않습니다.
- 현재 구현은 API 종류와 무관하게 동일 회원 내 주문번호 중복을 제한합니다.
- 네트워크 유실 상황에서 처리 여부를 확인할 수 있도록 주문번호 기반 거래조회 API를 제공합니다.

### 거래번호 정책

- 포인트 거래번호 `PTXNO`는 26자리 숫자 문자열입니다.
- 생성 규칙은 DB 현재시각 `yyyyMMddHHmmssSSS` 17자리와 DB sequence 9자리 조합입니다.
- 서버 간 clock skew 영향을 줄이기 위해 애플리케이션 시간이 아니라 DB 현재시각을 기준으로 생성합니다.
- 거래번호는 `POINT.SEQ_PTXNO`, 내부 상세 ID는 `POINT.SEQ_DETAIL_ID`를 사용합니다.

### 트랜잭션 정책

- 금액을 변경하는 API는 Facade 계층에서 단일 DB 트랜잭션으로 처리합니다.
- 거래 이력 `PNT_TR_HIST`는 Append-Only 정책입니다.
- 실패 시 잔액, 원장, 상세 이력, 거래 이력 변경을 모두 롤백합니다.

### 동시성 제어

- 포인트 금액 변경 API는 `memberId` 기준 요청 락을 적용합니다.
- 현재 락은 Caffeine 로컬 캐시 기반입니다.
- 동일 회원의 금액 변경 요청이 이미 처리 중이면 후속 요청은 대기하지 않고 실패합니다.
- 로컬 락 TTL 기본값은 `10s`입니다.
- 현재 구현은 단일 애플리케이션 인스턴스 실행을 전제로 합니다.
- 다중 인스턴스 운영 시 Redis 또는 DB 기반 분산 락으로 전환해야 합니다.

Redis 확장 시에는 다음 방식으로 전환할 수 있습니다.

```text
SET point:lock:{memberId} {lockToken} NX PX {ttl}
```

락 해제 시에는 다른 요청의 락을 삭제하지 않도록 저장된 token과 요청 token을 비교한 뒤 삭제합니다.

### 만료 처리

- 만료 API는 회원 단위로 처리합니다.
- 만료도 잔액 변경이 발생하므로 `memberId` 기준 요청 락을 적용합니다.
- 요청 기준 시각까지 만료된 해당 회원의 적립 원장만 처리합니다.
- 만료 처리 시 사용 가능한 잔액을 차감하고 누적 만료 금액을 증가시킵니다.

### 이력 조회

- 이력 조회는 `startDate`, `endDate`를 필수로 받습니다.
- 날짜 형식은 `yyyy-MM-dd`입니다.
- 조회 기간은 최대 3개월입니다.
- `startDate`는 `endDate`보다 이전이어야 합니다.
- `txType`은 선택값이며, 없으면 전체 거래 유형을 조회합니다.
- 조회 결과가 없으면 `NO_HISTORY_RESULT`를 반환합니다.

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

## 에러 응답

비즈니스 예외는 공통 에러 응답으로 반환합니다.

| ErrorCode | 메시지 |
|---|---|
| `INVALID_PARAMETER` | 요청 파라미터가 올바르지 않습니다. |
| `INVALID_USER` | 유효하지 않은 회원입니다. |
| `NOT_ENOUGH_POINT` | 포인트 잔액이 부족합니다. |
| `INCORRECT_POINT` | 포인트 금액이 올바르지 않습니다. |
| `ALREADY_CANCELED` | 이미 취소된 거래입니다. |
| `NO_POINT_HISTORY` | 포인트 거래 이력을 찾을 수 없습니다. |
| `PARTIAL_CANCEL_FAIL` | 부분취소를 처리할 수 없습니다. |
| `EXPIRED_POINT` | 만료된 포인트는 취소할 수 없습니다. |
| `DUPLICATED_ORDER` | 이미 처리된 주문번호입니다. |
| `NO_REMAIN_POINT` | 취소 가능한 잔여 포인트가 없습니다. |
| `INVALID_HISTORY_PERIOD` | 시작일은 종료일보다 이전이어야 합니다. |
| `HISTORY_PERIOD_EXCEEDED` | 조회 가능 기간이 아닙니다. |
| `NO_HISTORY_RESULT` | 조회 결과가 없습니다. |
| `POINT_PROCESSING` | 이미 동일 사용자 거래건이 처리중입니다. |

## 패키지 구조

```text
com.payment.point
├── api
├── application
├── config
├── domain
│   ├── balance
│   ├── earn
│   ├── transaction
│   └── use
└── support
```

## 구현 참고

- 상세 도메인 설계와 테이블 설명은 `docs/point_system_design.md`에 정리되어 있습니다.
- API 요청/응답 스키마는 `docs/openapi.json`에 정리되어 있습니다.
- 테이블 생성 DDL은 `src/main/resources/schema.sql`에 있습니다.
