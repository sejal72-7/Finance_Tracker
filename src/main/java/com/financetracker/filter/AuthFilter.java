package com.financetracker.filter;

import com.financetracker.util.JsonUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Set;

/**
 * Enforces UTF-8 encoding, cache control, and session authentication for protected views and API endpoints.
 */
@WebFilter("/*")
public class AuthFilter implements Filter {

    private static final Set<String> PUBLIC_API_PATHS = Set.of(
            "/api/health",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/logout",
            "/api/auth/me"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String ctx = req.getContextPath();
        String uri = req.getRequestURI();
        String path = uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;

        if (isPublicStatic(path) || PUBLIC_API_PATHS.contains(path)) {
            if (path.startsWith("/api/")) {
                resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            }
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        boolean authenticated = session != null && session.getAttribute("userId") != null;

        if (path.startsWith("/api/")) {
            resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            if (!authenticated) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        if ("/dashboard.html".equals(path)) {
            resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            resp.setHeader("Pragma", "no-cache");
            if (!authenticated) {
                resp.sendRedirect(ctx + "/index.html");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicStatic(String path) {
        return path.isEmpty()
                || "/".equals(path)
                || "/index.html".equals(path)
                || "/register.html".equals(path)
                || "/favicon.svg".equals(path)
                || path.startsWith("/css/")
                || path.startsWith("/js/");
    }
}

