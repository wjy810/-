package com.jobproof.modules.aibilling.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.modules.aibilling.application.AiChargeService;
import com.jobproof.shared.ai.AiExecutionContext;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

@ControllerAdvice
public class AiQuoteAcceptanceAdvice extends RequestBodyAdviceAdapter {
    private final AiChargeService charges;
    public AiQuoteAcceptanceAdvice(AiChargeService charges) { this.charges = charges; }
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return AiExecutionContext.current() != null && AiExecutionContext.current().quoteId() != null;
    }
    @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter, Type type,
            Class<? extends HttpMessageConverter<?>> converter) {
        charges.acceptHttp(SecurityConfig.currentAccount().accountId());
        return body;
    }
    @Override public Object handleEmptyBody(Object body, HttpInputMessage input, MethodParameter parameter, Type type,
            Class<? extends HttpMessageConverter<?>> converter) {
        charges.acceptHttp(SecurityConfig.currentAccount().accountId());
        return body;
    }
}
