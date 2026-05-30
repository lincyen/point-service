package com.payment.point.support;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 포인트 시스템의 26자리 숫자 문자열 ID 생성기.
 *
 * <p>DB 현재시각 17자리({@code yyyyMMddHHmmssSSS})와 DB sequence 9자리를 결합해
 * 거래번호와 내부 상세 ID를 생성한다.</p>
 */
@Component
public class PointIdGenerator {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String PTXNO_SEQUENCE_SQL = "select next value for POINT.SEQ_PTXNO";
    private static final String DETAIL_ID_SEQUENCE_SQL = "select next value for POINT.SEQ_DETAIL_ID";
    private static final String CURRENT_TIMESTAMP_SQL = "select current_timestamp";

    private final JdbcTemplate jdbcTemplate;

    public PointIdGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * <b>포인트 거래번호 생성</b>
     *
     * @return {@code POINT.SEQ_PTXNO} 기반의 26자리 숫자 문자열
     */
    public String generatePointTransactionNo() {
        return generate(PTXNO_SEQUENCE_SQL);
    }

    /**
     * 사용 Allocation 및 사용취소 상세 이력의 내부 상세 ID를 생성한다.
     *
     * @return {@code POINT.SEQ_DETAIL_ID} 기반의 26자리 숫자 문자열
     */
    public String generateDetailId() {
        return generate(DETAIL_ID_SEQUENCE_SQL);
    }

    /**
     * 지정된 sequence SQL을 사용해 공통 ID 포맷을 생성한다.
     *
     * @param sequenceSql ID 뒤 9자리에 사용할 sequence 조회 SQL
     * @return DB 현재시각 17자리와 sequence 9자리를 결합한 26자리 숫자 문자열
     */
    private String generate(String sequenceSql) {
        Timestamp timestamp = jdbcTemplate.queryForObject(CURRENT_TIMESTAMP_SQL, Timestamp.class);
        Long sequence = jdbcTemplate.queryForObject(sequenceSql, Long.class);
        if (timestamp == null || sequence == null) {
            throw new IllegalStateException("point id source is not available");
        }
        return TIMESTAMP_FORMATTER.format(timestamp.toLocalDateTime()) + "%09d".formatted(sequence);
    }
}
