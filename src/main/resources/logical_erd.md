# 무료 포인트 시스템 논리 ERD

이 문서는 무료 포인트 시스템의 핵심 도메인 관계를 간단히 표현한 논리 ERD입니다.
물리 테이블 정의, FK, 인덱스, 제약조건은 `schema.sql`을 기준으로 합니다.

```mermaid
erDiagram
    PNT_MEMBER_BAL {
        varchar MEMBER_ID PK "회원아이디"
        bigint NORMAL_AMT "일반 포인트 잔액"
        bigint MANUAL_AMT "수기 지급 포인트 잔액"
        bigint EXPIRED_AMT "누적 만료 금액"
        date NEXT_EXP_DT "다음 만료 예정일"
    }

    PNT_TR_HIST {
        varchar PTXNO PK "포인트 거래번호"
        varchar OPTXNO "원 거래번호"
        varchar MEMBER_ID "회원아이디"
        varchar ORDER_NO "주문번호"
        varchar TX_TYPE "거래 유형"
        bigint TX_AMT "거래 금액"
        bigint RMN_AMT "거래 후 회원 잔액"
    }

    PNT_EARN_MST {
        varchar PTXNO PK "적립 거래번호"
        varchar MEMBER_ID "회원아이디"
        varchar EARN_TYPE "NORMAL, MANUAL, RESTORE"
        bigint EARN_AMT "최초 적립 금액"
        bigint RMN_AMT "사용 가능 잔액"
        bigint USE_AMT "누적 사용 금액"
        bigint EARN_CNCL_AMT "적립취소 금액"
        bigint EXPIRED_AMT "만료 금액"
        date EXP_DT "현재 유효 만료일"
        varchar STATUS "ACTIVE, USED_UP, CNCL, EXPIRED"
    }

    PNT_USE_MST {
        varchar PTXNO PK "사용 거래번호"
        varchar MEMBER_ID "회원아이디"
        varchar ORDER_NO "주문번호"
        bigint USE_AMT "최초 사용 금액"
        bigint CNCL_AMT "누적 사용취소 금액"
        bigint RMN_AMT "취소 가능 잔액"
        varchar STATUS "ACTIVE, PARTIAL_CNCL, CNCL"
    }

    PNT_USE_ALLOC {
        varchar USE_ALLOC_ID PK "사용 상세번호"
        varchar PTXNO "사용 거래번호"
        varchar EARN_PTXNO "적립 거래번호"
        int PRIORITY "사용 차감 순서"
        bigint CNSM_AMT "차감 금액"
        bigint CNCL_AMT "취소 금액"
        bigint RMN_AMT "취소 가능 잔액"
    }

    PNT_USE_CANCEL_HIST {
        varchar USE_CNCL_HIST_ID PK "사용취소 상세번호"
        varchar USE_CNCL_PTXNO "사용취소 거래번호"
        varchar USE_PTXNO "원 사용 거래번호"
        varchar USE_ALLOC_ID "원 사용 상세번호"
        varchar ORG_EARN_PTXNO "원 적립 거래번호"
        varchar RESTORE_PTXNO "신규 RESTORE 적립 거래번호"
        varchar RESTORE_TYPE "ORIGINAL_RESTORE, NEW_EARN"
        bigint CNCL_AMT "취소 금액"
    }

    PNT_MEMBER_BAL ||--o{ PNT_EARN_MST : "보유한다"
    PNT_MEMBER_BAL ||--o{ PNT_USE_MST : "사용한다"
    PNT_MEMBER_BAL ||--o{ PNT_TR_HIST : "거래 이력을 가진다"

    PNT_EARN_MST ||--o{ PNT_USE_ALLOC : "사용 금액이 차감된다"
    PNT_USE_MST ||--|{ PNT_USE_ALLOC : "적립 건별로 배분한다"

    PNT_USE_MST ||--o{ PNT_USE_CANCEL_HIST : "전체 또는 부분 취소된다"
    PNT_USE_ALLOC ||--o{ PNT_USE_CANCEL_HIST : "배분 금액이 복원된다"
    PNT_EARN_MST o|--o{ PNT_USE_CANCEL_HIST : "만료 시 RESTORE 적립을 생성한다"
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

- `PNT_MEMBER_BAL`과 각 원장 사이의 관계는 `MEMBER_ID` 기반 논리 관계입니다.
- 물리 스키마에서는 원장 간 필수 참조 관계에만 FK를 적용합니다.
- 거래 이력은 원장과 FK로 연결하지 않고 거래번호와 원 거래번호로 추적합니다.
