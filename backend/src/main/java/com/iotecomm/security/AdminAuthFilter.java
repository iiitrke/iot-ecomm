package com.iotecomm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Protects /api/admin/** with a static X-Admin-Key header. */
public class AdminAuthFilter extends OncePerRequestFilter {
    private final String adminKey;

    public AdminAuthFilter(String adminKey) {
        this.adminKey = adminKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!Hashing.constantTimeEquals(adminKey, req.getHeader("X-Admin-Key"))) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid admin key");
            return;
        }
        chain.doFilter(req, res);
    }
}
