package com.ds.backend.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.security.auth.x500.X500Principal;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;

import static org.mockito.Mockito.*;

public class DispatcherCertFilterTest {

    private DispatcherCertFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new DispatcherCertFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldAllowWhenValidCert() throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        X500Principal principal = new X500Principal("CN=dispatcher");
        when(cert.getSubjectX500Principal()).thenReturn(principal);
        X509Certificate[] certs = new X509Certificate[]{cert};

        when(request.getRequestURI()).thenReturn("/api/auth/snapshot");
        when(request.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(certs);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldDenyWhenNoCert() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/snapshot");
        when(request.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(null);
        
        PrintWriter writer = mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldDenyWhenInvalidCN() throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        X500Principal principal = new X500Principal("CN=invalid");
        when(cert.getSubjectX500Principal()).thenReturn(principal);
        X509Certificate[] certs = new X509Certificate[]{cert};

        when(request.getRequestURI()).thenReturn("/api/auth/snapshot");
        when(request.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(certs);
        
        PrintWriter writer = mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }
}
