package com.financetracker.servlet;

import com.financetracker.dao.CategoryDAO;
import com.financetracker.model.Category;
import com.financetracker.util.DtoMapper;
import com.financetracker.util.JsonUtil;
import com.financetracker.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST-style Servlet for listing built-in categories and managing user custom categories.
 */
@WebServlet("/api/categories/*")
public class CategoryServlet extends BaseApiServlet {

    private final CategoryDAO categoryDAO = new CategoryDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return;
        }

        String rawType = ValidationUtil.trimToNull(req.getParameter("type"));
        String type = null;
        if (rawType != null) {
            type = ValidationUtil.normalizeType(rawType);
            if (type == null) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Type must be INCOME or EXPENSE.");
                return;
            }
        }

        try {
            List<Category> categories = categoryDAO.findAllForUser(userId, type);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("categories", DtoMapper.toCategoryList(categories));
            sendSuccess(resp, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return;
        }

        Map<String, Object> data;
        try {
            data = JsonUtil.readRequestData(req);
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON payload.");
            return;
        }

        String name = ValidationUtil.trimToNull(JsonUtil.getString(data, "name"));
        String type = ValidationUtil.normalizeType(JsonUtil.getString(data, "type"));
        String color = ValidationUtil.trimToNull(JsonUtil.getString(data, "color"));
        String icon = ValidationUtil.trimToNull(JsonUtil.getString(data, "icon"));

        if (name == null || name.length() < 2 || name.length() > 60) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Category name must be 2 to 60 characters.");
            return;
        }
        if (type == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Type must be INCOME or EXPENSE.");
            return;
        }
        if (color == null || !ValidationUtil.isValidHexColor(color)) {
            color = "INCOME".equals(type) ? "#10B981" : "#6366F1";
        }
        if (icon == null || icon.length() > 40) {
            icon = "tag";
        }

        try {
            if (categoryDAO.existsForUser(userId, name, type, null)) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_CONFLICT,
                        "A category with this name and type already exists.");
                return;
            }
            Category category = new Category();
            category.setUserId(userId);
            category.setName(name);
            category.setType(type);
            category.setColor(color);
            category.setIcon(icon);
            Category created = categoryDAO.create(category);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("category", DtoMapper.toCategoryMap(created));
            sendSuccess(resp, HttpServletResponse.SC_CREATED, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return;
        }
        Integer id = extractPathId(req);
        if (id == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Valid category ID is required.");
            return;
        }

        Map<String, Object> data;
        try {
            data = JsonUtil.readRequestData(req);
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON payload.");
            return;
        }

        String name = ValidationUtil.trimToNull(JsonUtil.getString(data, "name"));
        String color = ValidationUtil.trimToNull(JsonUtil.getString(data, "color"));
        String icon = ValidationUtil.trimToNull(JsonUtil.getString(data, "icon"));

        if (name == null || name.length() < 2 || name.length() > 60) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Category name must be 2 to 60 characters.");
            return;
        }

        try {
            Category existing = categoryDAO.findByIdAccessible(id, userId);
            if (existing == null) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Category not found.");
                return;
            }
            if (existing.isSystemDefault()) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Built-in categories cannot be modified.");
                return;
            }
            if (categoryDAO.existsForUser(userId, name, existing.getType(), id)) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_CONFLICT,
                        "Another category with this name already exists.");
                return;
            }

            existing.setName(name);
            if (color != null && ValidationUtil.isValidHexColor(color)) {
                existing.setColor(color);
            }
            if (icon != null && icon.length() <= 40) {
                existing.setIcon(icon);
            }
            categoryDAO.updateCustom(existing);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("category", DtoMapper.toCategoryMap(existing));
            sendSuccess(resp, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return;
        }
        Integer id = extractPathId(req);
        if (id == null) {
            id = ValidationUtil.parsePositiveInt(req.getParameter("id"));
        }
        if (id == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Valid category ID is required.");
            return;
        }

        try {
            Category existing = categoryDAO.findByIdAccessible(id, userId);
            if (existing == null) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Category not found.");
                return;
            }
            if (existing.isSystemDefault()) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Built-in categories cannot be deleted.");
                return;
            }
            if (categoryDAO.isCategoryInUse(id, userId)) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_CONFLICT,
                        "Cannot delete a category that has existing transactions.");
                return;
            }
            categoryDAO.deleteCustom(id, userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "Category deleted.");
            sendSuccess(resp, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }
}

