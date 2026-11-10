package com.financetracker.servlet;

import com.financetracker.dao.UserDAO;
import com.financetracker.model.User;
import com.financetracker.util.DtoMapper;
import com.financetracker.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returns the currently authenticated user profile from the active HTTP session.
 */
@WebServlet("/api/auth/me")
public class AuthMeServlet extends BaseApiServlet {

    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated.");
            return;
        }
        try {
            User user = userDAO.findById(userId);
            if (user == null) {
                HttpSession session = req.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Session is no longer valid.");
                return;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("authenticated", true);
            result.put("user", DtoMapper.toUserMap(user));
            sendSuccess(resp, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }
}

