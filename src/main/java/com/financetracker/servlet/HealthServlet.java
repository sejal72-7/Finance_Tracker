package com.financetracker.servlet;

import com.financetracker.config.DBConnection;
import com.financetracker.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint verifying servlet container and JDBC database connectivity.
 */
@WebServlet("/api/health")
public class HealthServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boolean dbOk = DBConnection.testConnection();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbOk ? "UP" : "DEGRADED");
        body.put("database", dbOk);
        JsonUtil.sendJson(resp, dbOk ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE, body);
    }
}

