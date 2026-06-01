package com.payment.point.support;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 회원아이디 검증 어노테이션.
 *
 * <pre>
 *     회원아이디는 공백 문자를 포함하지 않는 1자 이상 32자 이하 문자열만 허용한다.
 * </pre>
 */
@Documented
@Constraint(validatedBy = {})
@NotBlank
@Size(max = 32)
@Pattern(regexp = "^\\S+$")
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMemberId {

    String message() default "invalid memberId";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
