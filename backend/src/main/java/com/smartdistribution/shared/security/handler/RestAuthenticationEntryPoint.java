package com.smartdistribution.shared.security.handler;

import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        ErrorCode errorCode = (ErrorCode) request.getAttribute(
                JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE
        );

        if (errorCode == null) {
            errorCode = ErrorCode.AUTHENTICATION_REQUIRED;
        }

        errorResponseWriter.write(
                request,
                response,
                errorCode
        );
    }
}
