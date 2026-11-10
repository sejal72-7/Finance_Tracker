package com.financetracker.servlet;

import com.financetracker.util.JsonUtil;
import com.financetracker.util.ValidationUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared base servlet providing session extraction, path-ID parsing, and uniform DB error responses.
 */
public abstract class BaseApiServlet extends HttpServlet {

    protected Integer getAuthenticatedUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute("userId");
        return attr instanceof Integer id ? id : null;
    }

    protected Integer extractPathId(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return null;
        }
        String clean = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if (clean.contains("/")) {
            return null;
        }
        return ValidationUtil.parsePositiveInt(clean);
    }

    protected void sendSuccess(HttpServletResponse resp, int status, Map<String, Object> data) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        if (data != null) {
            payload.putAll(data);
        }
        JsonUtil.sendJson(resp, status, payload);
    }

    protected void handleSqlException(HttpServletRequest req, HttpServletResponse resp, SQLException ex)
            throws IOException {
        getServletContext().log("Database error on " + req.getRequestURI(), ex);
        boolean connectionIssue = ex instanceof SQLNonTransientConnectionException
                || (ex.getSQLState() != null && ex.getSQLState().startsWith("08"));
        if (connectionIssue) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Database connection failed. Check MySQL service and db.properties configuration.");
        } else {
            JsonUtil.sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "A database error occurred while processing your request.");
        }
    }
}

