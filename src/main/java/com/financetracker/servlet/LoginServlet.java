package com.financetracker.servlet;

import com.financetracker.dao.UserDAO;
import com.financetracker.model.User;
import com.financetracker.util.DtoMapper;
import com.financetracker.util.JsonUtil;
import com.financetracker.util.PasswordUtil;
import com.financetracker.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authenticates registered users and establishes an HTTP session.
 */
@WebServlet("/api/auth/login")
public class LoginServlet extends BaseApiServlet {

    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> data;
        try {
            data = JsonUtil.readRequestData(req);
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid request payload.");
            return;
        }

        String email = ValidationUtil.trimToNull(JsonUtil.getString(data, "email"));
        String password = JsonUtil.getString(data, "password");

        if (!ValidationUtil.isValidEmail(email) || password == null || password.isEmpty()) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Email and password are required.");
            return;
        }

        try {
            User user = userDAO.findByEmail(email);
            if (user == null || !PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid email or password.");
                return;
            }

            HttpSession existing = req.getSession(false);
            if (existing != null) {
                existing.invalidate();
            }
            HttpSession session = req.getSession(true);
            session.setAttribute("userId", user.getId());
            session.setAttribute("userName", user.getFullName());
            session.setAttribute("userEmail", user.getEmail());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("user", DtoMapper.toUserMap(user));
            sendSuccess(resp, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }
}

