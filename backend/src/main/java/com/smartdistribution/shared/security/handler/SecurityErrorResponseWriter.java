package com.smartdistribution.shared.security.handler;

import com.smartdistribution.shared.exception.ApiErrorResponse;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.web.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {

        //建立 Error Response Body
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                errorCode.name(),
                errorCode.getDefaultMessage(),
                List.of(),
                (String) request.getAttribute(RequestIdFilter.TRACE_ID_ATTRIBUTE)
        );

        //設定 HTTP 狀態碼與回應格式
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        //將 response 序列化為 JSON 並寫入 Response Body
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
