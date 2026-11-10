package com.financetracker.dao;

import com.financetracker.config.DBConnection;
import com.financetracker.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for Category CRUD operations using PreparedStatement.
 */
public class CategoryDAO {

    public List<Category> findAllForUser(int userId, String type) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, name, type, color, icon FROM categories WHERE (user_id IS NULL OR user_id = ?)"
        );
        if (type != null) {
            sql.append(" AND type = ?");
        }
        sql.append(" ORDER BY type ASC, name ASC");

        List<Category> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, userId);
            if (type != null) {
                ps.setString(2, type);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public Category findByIdAccessible(int categoryId, int userId) throws SQLException {
        String sql = "SELECT id, user_id, name, type, color, icon FROM categories "
                + "WHERE id = ? AND (user_id IS NULL OR user_id = ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public boolean existsForUser(int userId, String name, String type, Integer excludeId) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT 1 FROM categories WHERE (user_id IS NULL OR user_id = ?) AND LOWER(name) = LOWER(?) AND type = ?"
        );
        if (excludeId != null) {
            sql.append(" AND id <> ?");
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, userId);
            ps.setString(2, name);
            ps.setString(3, type);
            if (excludeId != null) {
                ps.setInt(4, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Category create(Category category) throws SQLException {
        String sql = "INSERT INTO categories (user_id, name, type, color, icon) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (category.getUserId() == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, category.getUserId());
            }
            ps.setString(2, category.getName());
            ps.setString(3, category.getType());
            ps.setString(4, category.getColor());
            ps.setString(5, category.getIcon());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    category.setId(keys.getInt(1));
                }
            }
            return category;
        }
    }

    public boolean updateCustom(Category category) throws SQLException {
        String sql = "UPDATE categories SET name = ?, color = ?, icon = ? WHERE id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getColor());
            ps.setString(3, category.getIcon());
            ps.setInt(4, category.getId());
            ps.setInt(5, category.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean isCategoryInUse(int categoryId, int userId) throws SQLException {
        String sql = "SELECT 1 FROM transactions WHERE category_id = ? AND user_id = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean deleteCustom(int categoryId, int userId) throws SQLException {
        String sql = "DELETE FROM categories WHERE id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        int rawUserId = rs.getInt("user_id");
        Integer userId = rs.wasNull() ? null : rawUserId;
        return new Category(
                rs.getInt("id"),
                userId,
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("color"),
                rs.getString("icon")
        );
    }
}

