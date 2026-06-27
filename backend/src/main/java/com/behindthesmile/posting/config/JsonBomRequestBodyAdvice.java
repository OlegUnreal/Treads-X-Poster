package com.behindthesmile.posting.config;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@ControllerAdvice(annotations = {Controller.class, org.springframework.web.bind.annotation.RestController.class})
public class JsonBomRequestBodyAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) throws IOException {
        if (!hasBody(inputMessage.getHeaders())) {
            return inputMessage;
        }

        byte[] body = inputMessage.getBody().readAllBytes();
        byte[] sanitized = sanitizeBody(body, inputMessage.getHeaders().getContentType());
        return new SanitizedHttpInputMessage(inputMessage.getHeaders(), sanitized);
    }

    private boolean hasBody(HttpHeaders headers) {
        long contentLength = headers.getContentLength();
        return contentLength != 0;
    }

    private byte[] sanitizeBody(byte[] body, MediaType contentType) {
        byte[] sanitized = BomUtils.stripBom(body);
        Charset charset = contentType != null && contentType.getCharset() != null
                ? contentType.getCharset()
                : StandardCharsets.UTF_8;
        String text = new String(sanitized, charset);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
            return text.getBytes(charset);
        }
        return sanitized;
    }

    private record SanitizedHttpInputMessage(HttpHeaders headers, byte[] body) implements HttpInputMessage {
        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
