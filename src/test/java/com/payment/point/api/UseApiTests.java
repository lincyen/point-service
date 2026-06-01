package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.payment.point.api.earn.EarnResponse;
import com.payment.point.api.use.UseRequest;
import com.payment.point.api.use.UseResponse;
import com.payment.point.domain.balance.PntMemberBal;
import com.payment.point.domain.balance.PntMemberBalRepository;
import com.payment.point.domain.earn.PntEarnMst;
import com.payment.point.domain.earn.PntEarnMstRepository;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.domain.earn.EarnStatus;
import com.payment.point.domain.use.PntUseAlloc;
import com.payment.point.domain.use.PntUseAllocRepository;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class UseApiTests extends PointApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PntMemberBalRepository pntMemberBalRepository;

    @Autowired
    private PntEarnMstRepository pntEarnMstRepository;

    @Autowired
    private PntUseAllocRepository pntUseAllocRepository;

    @Test
    @DisplayName("성공-포인트 사용")
    void useConsumesPoint() {
        String memberId = memberId();
        givenEarn(memberId, "USE-API-EARN", EarnType.NORMAL, 1_000, "P10D");

        UseResponse response = givenUse(memberId, "USE-API", 400);

        assertPointId(response.pointTransactionNo());
        assertEquals(memberId, response.memberId());
        assertEquals(400, response.amount());
        assertEquals(600, response.remainingAmount());
    }

    @Test
    @DisplayName("실패-포인트 잔액 부족, NOT_ENOUGH_POINT")
    void useRejectsNotEnoughPoint() {
        String memberId = memberId();
        givenEarn(memberId, "USE-API-EARN", EarnType.NORMAL, 100, "P10D");

        ApiException exception = assertThrows(ApiException.class,
                () -> givenUse(memberId, "USE-API", 200));

        assertEquals(ErrorCode.NOT_ENOUGH_POINT, exception.getErrorCode());
    }

    @Test
    @DisplayName("실패-동일 주문번호 사용 재호출, DUPLICATED_ORDER")
    void useRejectsDuplicatedOrderNoWithoutAdditionalBalanceChange() {
        String memberId = memberId();
        givenEarn(memberId, "USE-DUPLICATE-EARN", EarnType.NORMAL, 1_000, "P10D");
        UseRequest request = new UseRequest(orderNo("USE-DUPLICATE"), null, 400);

        pointFacadeService.use(memberId, request);

        ApiException exception = assertThrows(ApiException.class,
                () -> pointFacadeService.use(memberId, request));

        assertEquals(ErrorCode.DUPLICATED_ORDER, exception.getErrorCode());
        assertEquals(600, pointFacadeService.getBalance(memberId).totalAmount());
    }

    @Test
    @DisplayName("성공-다음 만료 예정일 도래 시 회원 만료 선처리 후 포인트 사용")
    void useExpiresMemberEarnsOnlyWhenNextExpireDateIsDue() {
        String memberId = memberId();
        EarnResponse expiredEarn = givenEarn(memberId, "USE-EXPIRE-DUE", EarnType.MANUAL, 100, "P10D");
        EarnResponse usableEarn = givenEarn(memberId, "USE-EXPIRE-USABLE", EarnType.NORMAL, 100, "P20D");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        jdbcTemplate.update("update POINT.PNT_EARN_MST set EXP_DT = ? where PTXNO = ?", yesterday,
                expiredEarn.pointTransactionNo());
        jdbcTemplate.update("update POINT.PNT_MEMBER_BAL set NEXT_EXP_DT = ? where MEMBER_ID = ?", yesterday, memberId);

        UseResponse response = givenUse(memberId, "USE-EXPIRE", 50);

        PntMemberBal balance = pntMemberBalRepository.findById(memberId).orElseThrow();
        PntEarnMst expiredEarnMst = pntEarnMstRepository.findById(expiredEarn.pointTransactionNo()).orElseThrow();
        PntEarnMst usableEarnMst = pntEarnMstRepository.findById(usableEarn.pointTransactionNo()).orElseThrow();
        assertEquals(50, response.remainingAmount());
        assertEquals(100, balance.getExpiredAmount());
        assertEquals(LocalDate.now().plusDays(20), balance.getNextExpireDate());
        assertEquals(EarnStatus.EXPIRED, expiredEarnMst.getStatus());
        assertEquals(50, usableEarnMst.getRemainingAmount());
    }

    @Test
    @DisplayName("실패-만료일 당일 포인트 사용 요청, NOT_ENOUGH_POINT")
    void useRejectsEarnOnExpireDate() {
        String memberId = memberId();
        EarnResponse earnResponse = givenEarn(memberId, "USE-EXPIRE-TODAY-EARN", EarnType.NORMAL, 100, "P10D");
        LocalDate today = LocalDate.now();
        jdbcTemplate.update("update POINT.PNT_EARN_MST set EXP_DT = ? where PTXNO = ?", today,
                earnResponse.pointTransactionNo());
        jdbcTemplate.update("update POINT.PNT_MEMBER_BAL set NEXT_EXP_DT = ? where MEMBER_ID = ?", today, memberId);

        ApiException exception = assertThrows(ApiException.class,
                () -> givenUse(memberId, "USE-EXPIRE-TODAY", 1));

        PntEarnMst earn = pntEarnMstRepository.findById(earnResponse.pointTransactionNo()).orElseThrow();
        PntMemberBal balance = pntMemberBalRepository.findById(memberId).orElseThrow();
        assertEquals(ErrorCode.NOT_ENOUGH_POINT, exception.getErrorCode());
        assertEquals(EarnStatus.ACTIVE, earn.getStatus());
        assertEquals(0, balance.getExpiredAmount());
        assertEquals(100, balance.getTotalAmount());
    }

    @Test
    @DisplayName("성공-MANUAL 적립 원장을 일반 적립 원장보다 우선 차감")
    void useConsumesManualEarnBeforeNormalEarn() {
        String memberId = memberId();
        EarnResponse normalEarn = givenEarn(memberId, "USE-PRIORITY-NORMAL", EarnType.NORMAL, 100, "P2D");
        EarnResponse manualEarn = givenEarn(memberId, "USE-PRIORITY-MANUAL", EarnType.MANUAL, 100, "P10D");

        UseResponse useResponse = givenUse(memberId, "USE-PRIORITY-MANUAL-FIRST", 50);

        List<PntUseAlloc> allocations = pntUseAllocRepository.findByPtxnoOrderByPriorityAsc(
                useResponse.pointTransactionNo()
        );
        assertEquals(1, allocations.size());
        assertEquals(manualEarn.pointTransactionNo(), allocations.getFirst().getEarnPtxno());
        assertEquals(100, pntEarnMstRepository.findById(normalEarn.pointTransactionNo()).orElseThrow().getRemainingAmount());
    }

    @Test
    @DisplayName("성공-동일 적립 유형이면 만료일이 빠른 적립 원장을 우선 차감")
    void useConsumesEarlierExpireEarnFirst() {
        String memberId = memberId();
        EarnResponse laterEarn = givenEarn(memberId, "USE-PRIORITY-LATER", EarnType.NORMAL, 100, "P10D");
        EarnResponse earlierEarn = givenEarn(memberId, "USE-PRIORITY-EARLIER", EarnType.NORMAL, 100, "P2D");

        UseResponse useResponse = givenUse(memberId, "USE-PRIORITY-EARLIER-FIRST", 50);

        List<PntUseAlloc> allocations = pntUseAllocRepository.findByPtxnoOrderByPriorityAsc(
                useResponse.pointTransactionNo()
        );
        assertEquals(1, allocations.size());
        assertEquals(earlierEarn.pointTransactionNo(), allocations.getFirst().getEarnPtxno());
        assertEquals(100, pntEarnMstRepository.findById(laterEarn.pointTransactionNo()).orElseThrow().getRemainingAmount());
    }

    @Test
    @DisplayName("실패-존재하지 않는 사용 원장을 참조하는 Allocation 등록, FK 제약 위반")
    void useAllocationRejectsUnknownUseMasterReference() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                insert into POINT.PNT_USE_ALLOC (
                    USE_ALLOC_ID, PTXNO, EARN_PTXNO, MEMBER_ID, PRIORITY,
                    CNSM_AMT, CNCL_AMT, RMN_AMT, EXP_DT, CREATED_AT, UPDATED_AT
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                "99999999999999999999999991",
                "99999999999999999999999992",
                "99999999999999999999999993",
                memberId(),
                1,
                100,
                0,
                100,
                LocalDate.now().plusDays(1)
        ));
    }

    @Test
    @DisplayName("실패-Allocation이 참조 중인 사용 원장 단독 삭제, FK RESTRICT")
    void useMasterDeleteRejectsWhenAllocationExists() {
        String memberId = memberId();
        givenEarn(memberId, "USE-FK-EARN", EarnType.NORMAL, 100, "P10D");
        UseResponse useResponse = givenUse(memberId, "USE-FK", 50);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("delete from POINT.PNT_USE_MST where PTXNO = ?",
                        useResponse.pointTransactionNo()));
    }
}
