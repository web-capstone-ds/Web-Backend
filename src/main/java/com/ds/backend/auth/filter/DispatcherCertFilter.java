package com.ds.backend.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.cert.X509Certificate;

@Component
public class DispatcherCertFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DispatcherCertFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().equals("/api/auth/snapshot")) {
            X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");

            if (certs == null || certs.length == 0) {
                log.warn("auth_snapshot_rejected reason=missing_client_certificate remoteAddr={} userAgent={}",
                        clientIp(request),
                        request.getHeader("User-Agent"));
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\": \"Forbidden: Client certificate required\"}");
                return;
            }

            X509Certificate clientCert = certs[0];
            String subjectDN = clientCert.getSubjectX500Principal().getName();
            
            if (!subjectDN.contains("CN=dispatcher")) {
                log.warn("auth_snapshot_rejected reason=invalid_certificate_cn subjectDN={} remoteAddr={} userAgent={}",
                        subjectDN,
                        clientIp(request),
                        request.getHeader("User-Agent"));
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\": \"Forbidden: Invalid certificate CN\"}");
                return;
            }

            log.info("auth_snapshot_cert_accepted subjectDN={} remoteAddr={} userAgent={}",
                    subjectDN,
                    clientIp(request),
                    request.getHeader("User-Agent"));
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
