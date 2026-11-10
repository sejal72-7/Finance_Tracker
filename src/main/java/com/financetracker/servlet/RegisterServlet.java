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
 * Handles new user registration, password hashing, and initial session creation.
 */
@WebServlet("/api/auth/register")
public class RegisterServlet extends BaseApiServlet {

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

        String fullName = ValidationUtil.trimToNull(JsonUtil.getString(data, "fullName"));
        String email = ValidationUtil.trimToNull(JsonUtil.getString(data, "email"));
        String password = JsonUtil.getString(data, "password");
        String confirmPassword = JsonUtil.getString(data, "confirmPassword");

        if (fullName == null || fullName.length() < 2 || fullName.length() > 100) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Full name must be between 2 and 100 characters.");
            return;
        }
        if (!ValidationUtil.isValidEmail(email)) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Please enter a valid email address.");
            return;
        }
        if (password == null || password.length() < 6 || password.length() > 128) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Password must be at least 6 characters.");
            return;
        }
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Passwords do not match.");
            return;
        }

        try {
            if (userDAO.findByEmail(email) != null) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_CONFLICT, "An account with this email already exists.");
                return;
            }

            User user = new User();
            user.setFullName(fullName);
            user.setEmail(email.toLowerCase());
            user.setPasswordHash(PasswordUtil.hashPassword(password));
            User created = userDAO.create(user);

            HttpSession existing = req.getSession(false);
            if (existing != null) {
                existing.invalidate();
            }
            HttpSession session = req.getSession(true);
            session.setAttribute("userId", created.getId());
            session.setAttribute("userName", created.getFullName());
            session.setAttribute("userEmail", created.getEmail());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("user", DtoMapper.toUserMap(created));
            sendSuccess(resp, HttpServletResponse.SC_CREATED, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }
}

