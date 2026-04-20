package com.orda.backend.common.exception;

/**
 * 사용자에게 메시지를 그대로 노출해도 안전한 비즈니스 룰 위반 예외.
 * 외부 라이브러리(Hibernate/Validator/JJWT 등)가 던지는 IllegalArgumentException과
 * 분리하여 의도된 메시지만 응답 바디에 노출하기 위함.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
