package com.jobproof.infrastructure.web;

import com.jobproof.shared.ai.AiExecutionContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Hash the bytes actually received; never trust a digest supplied by the browser. */
@ControllerAdvice
public class AiRequestBodyAdvice extends RequestBodyAdviceAdapter {
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return AiExecutionContext.current() != null;
    }
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
            Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        byte[] bytes = input.getBody().readAllBytes();
        var context = AiExecutionContext.current();
        AiExecutionContext.set(new AiExecutionContext.State(context.quoteId(), context.path(),
                AiRequestContextConfiguration.hash(bytes), context.paymentConfirmed(), null, null, null, context.taskId(), context.operationId()));
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(bytes); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
}
