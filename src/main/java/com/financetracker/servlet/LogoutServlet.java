package com.financetracker.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Invalidates the active HTTP session and clears the session cookie on logout.
 */
@WebServlet("/api/auth/logout")
public class LogoutServlet extends BaseApiServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        invalidateSession(req, resp);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Logged out successfully.");
        sendSuccess(resp, HttpServletResponse.SC_OK, result);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        invalidateSession(req, resp);
        resp.sendRedirect(req.getContextPath() + "/index.html?loggedOut=1");
    }

    private void invalidateSession(HttpServletRequest req, HttpServletResponse resp) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        Cookie cookie = new Cookie("JSESSIONID", "");
        String ctx = req.getContextPath();
        cookie.setPath(ctx.isEmpty() ? "/" : ctx);
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        resp.addCookie(cookie);
    }
}

