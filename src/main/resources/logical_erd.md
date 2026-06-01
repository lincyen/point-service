# 무료 포인트 시스템 논리 ERD

이 문서는 무료 포인트 시스템의 핵심 도메인 관계를 간단히 표현한 논리 ERD입니다.
물리 테이블 정의, FK, 인덱스, 제약조건은 `schema.sql`을 기준으로 합니다.

```mermaid
erDiagram
    MEMBER_BALANCE {
        string memberId PK "회원아이디"
        long normalAmount "일반 포인트 잔액"
        long manualAmount "수기 지급 포인트 잔액"
        long expiredAmount "누적 만료 금액"
        date nextExpireDate "다음 만료 예정일"
    }

    POINT_TRANSACTION_HISTORY {
        string pointTransactionNo PK "포인트 거래번호"
        string originalPointTransactionNo "원 거래번호"
        string memberId "회원아이디"
        string orderNo "주문번호"
        string transactionType "거래 유형"
        long transactionAmount "거래 금액"
        long remainingAmount "거래 후 회원 잔액"
    }

    EARN_LEDGER {
        string pointTransactionNo PK "적립 거래번호"
        string memberId "회원아이디"
        string earnType "NORMAL, MANUAL, RESTORE"
        long earnAmount "최초 적립 금액"
        long remainingAmount "사용 가능 잔액"
        long useAmount "누적 사용 금액"
        long earnCancelAmount "적립취소 금액"
        long expiredAmount "만료 금액"
        date expireDate "현재 유효 만료일"
        string status "ACTIVE, USED_UP, CNCL, EXPIRED"
    }

    USE_LEDGER {
        string pointTransactionNo PK "사용 거래번호"
        string memberId "회원아이디"
        string orderNo "주문번호"
        long useAmount "최초 사용 금액"
        long cancelAmount "누적 사용취소 금액"
        long remainingAmount "취소 가능 잔액"
        string status "ACTIVE, PARTIAL_CNCL, CNCL"
    }

    USE_ALLOCATION {
        string useAllocationId PK "사용 상세번호"
        string usePointTransactionNo "사용 거래번호"
        string earnPointTransactionNo "적립 거래번호"
        int priority "사용 차감 순서"
        long consumeAmount "차감 금액"
        long cancelAmount "취소 금액"
        long remainingAmount "취소 가능 잔액"
    }

    USE_CANCEL_HISTORY {
        string useCancelHistoryId PK "사용취소 상세번호"
        string useCancelPointTransactionNo "사용취소 거래번호"
        string usePointTransactionNo "원 사용 거래번호"
        string useAllocationId "원 사용 상세번호"
        string originalEarnPointTransactionNo "원 적립 거래번호"
        string restorePointTransactionNo "신규 RESTORE 적립 거래번호"
        string restoreType "ORIGINAL_RESTORE, NEW_EARN"
        long cancelAmount "취소 금액"
    }

    MEMBER_BALANCE ||--o{ EARN_LEDGER : "보유한다"
    MEMBER_BALANCE ||--o{ USE_LEDGER : "사용한다"
    MEMBER_BALANCE ||--o{ POINT_TRANSACTION_HISTORY : "거래 이력을 가진다"

    EARN_LEDGER ||--o{ USE_ALLOCATION : "사용 금액이 차감된다"
    USE_LEDGER ||--|{ USE_ALLOCATION : "적립 건별로 배분한다"

    USE_LEDGER ||--o{ USE_CANCEL_HISTORY : "전체 또는 부분 취소된다"
    USE_ALLOCATION ||--o{ USE_CANCEL_HISTORY : "배분 금액이 복원된다"
    EARN_LEDGER o|--o{ USE_CANCEL_HISTORY : "만료 시 RESTORE 적립을 생성한다"
```

## 관계 설명

| 관계 | 설명 |
|---|---|
| 회원 잔액 - 적립 원장 | 회원별 적립 건과 사용 가능한 잔액을 관리합니다. |
| 회원 잔액 - 사용 원장 | 회원이 주문에서 사용한 포인트와 취소 가능 금액을 관리합니다. |
| 회원 잔액 - 거래 이력 | 적립, 적립취소, 사용, 사용취소, 만료 이력을 Append-Only 방식으로 기록합니다. |
| 적립 원장 - 사용 Allocation | 어떤 적립 건이 어떤 사용 거래에서 얼마만큼 차감되었는지 1원 단위로 추적합니다. |
| 사용 원장 - 사용취소 이력 | 전체 또는 부분 사용취소 결과를 추적합니다. |
| 적립 원장 - 사용취소 이력 | 만료 전에는 원 적립 건을 복원하고, 만료 후에는 신규 RESTORE 적립을 생성합니다. |

## 참고

- `MEMBER_BALANCE`와 각 원장 사이의 관계는 `memberId` 기반 논리 관계입니다.
- 물리 스키마에서는 원장 간 필수 참조 관계에만 FK를 적용합니다.
- 거래 이력은 원장과 FK로 연결하지 않고 거래번호와 원 거래번호로 추적합니다.
