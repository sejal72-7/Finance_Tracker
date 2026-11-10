package com.financetracker.dao;

import com.financetracker.config.DBConnection;
import com.financetracker.model.CategorySummary;
import com.financetracker.model.DashboardSummary;
import com.financetracker.model.MonthlySummary;
import com.financetracker.model.Transaction;
import com.financetracker.model.TransactionFilter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JDBC Data Access Object for Transaction CRUD, search/filtering, and analytics queries.
 */
public class TransactionDAO {

    public static final class FilteredTotals {
        public final int count;
        public final BigDecimal incomeTotal;
        public final BigDecimal expenseTotal;

        public FilteredTotals(int count, BigDecimal incomeTotal, BigDecimal expenseTotal) {
            this.count = count;
            this.incomeTotal = incomeTotal;
            this.expenseTotal = expenseTotal;
        }
    }

    public Transaction create(Transaction tx) throws SQLException {
        String sql = "INSERT INTO transactions (user_id, category_id, type, amount, description, notes, transaction_date) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, tx.getUserId());
            ps.setInt(2, tx.getCategoryId());
            ps.setString(3, tx.getType());
            ps.setBigDecimal(4, tx.getAmount());
            ps.setString(5, tx.getDescription());
            ps.setString(6, tx.getNotes());
            ps.setDate(7, Date.valueOf(tx.getTransactionDate()));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    tx.setId(keys.getInt(1));
                }
            }
            return findById(tx.getId(), tx.getUserId());
        }
    }

    public Transaction findById(int id, int userId) throws SQLException {
        String sql = "SELECT t.id, t.user_id, t.category_id, c.name AS category_name, "
                + "c.color AS category_color, c.icon AS category_icon, "
                + "t.type, t.amount, t.description, t.notes, t.transaction_date, t.created_at, t.updated_at "
                + "FROM transactions t "
                + "INNER JOIN categories c ON t.category_id = c.id "
                + "WHERE t.id = ? AND t.user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<Transaction> findAll(TransactionFilter filter) throws SQLException {
        List<Object> params = new ArrayList<>();
        String where = buildWhereClause(filter, params);

        String sql = "SELECT t.id, t.user_id, t.category_id, c.name AS category_name, "
                + "c.color AS category_color, c.icon AS category_icon, "
                + "t.type, t.amount, t.description, t.notes, t.transaction_date, t.created_at, t.updated_at "
                + "FROM transactions t "
                + "INNER JOIN categories c ON t.category_id = c.id "
                + where
                + " ORDER BY t.transaction_date DESC, t.id DESC LIMIT ? OFFSET ?";
        params.add(filter.getLimit());
        params.add(filter.getOffset());

        List<Transaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public FilteredTotals getFilteredTotals(TransactionFilter filter) throws SQLException {
        List<Object> params = new ArrayList<>();
        String where = buildWhereClause(filter, params);

        String sql = "SELECT COUNT(*) AS cnt, "
                + "COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS inc_sum, "
                + "COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS exp_sum "
                + "FROM transactions t "
                + "INNER JOIN categories c ON t.category_id = c.id "
                + where;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FilteredTotals(
                            rs.getInt("cnt"),
                            nonNull(rs.getBigDecimal("inc_sum")),
                            nonNull(rs.getBigDecimal("exp_sum"))
                    );
                }
            }
        }
        return new FilteredTotals(0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public boolean update(Transaction tx) throws SQLException {
        String sql = "UPDATE transactions "
                + "SET category_id = ?, type = ?, amount = ?, description = ?, notes = ?, transaction_date = ? "
                + "WHERE id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tx.getCategoryId());
            ps.setString(2, tx.getType());
            ps.setBigDecimal(3, tx.getAmount());
            ps.setString(4, tx.getDescription());
            ps.setString(5, tx.getNotes());
            ps.setDate(6, Date.valueOf(tx.getTransactionDate()));
            ps.setInt(7, tx.getId());
            ps.setInt(8, tx.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id, int userId) throws SQLException {
        String sql = "DELETE FROM transactions WHERE id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public DashboardSummary getDashboardSummary(int userId, int year, int month) throws SQLException {
        String sql = "SELECT "
                + "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS total_income, "
                + "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS total_expense, "
                + "COALESCE(SUM(CASE WHEN type = 'INCOME' AND YEAR(transaction_date) = ? AND MONTH(transaction_date) = ? THEN amount ELSE 0 END), 0) AS month_income, "
                + "COALESCE(SUM(CASE WHEN type = 'EXPENSE' AND YEAR(transaction_date) = ? AND MONTH(transaction_date) = ? THEN amount ELSE 0 END), 0) AS month_expense, "
                + "COUNT(*) AS tx_count, "
                + "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN 1 ELSE 0 END), 0) AS income_count, "
                + "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN 1 ELSE 0 END), 0) AS expense_count "
                + "FROM transactions WHERE user_id = ?";

        DashboardSummary summary = new DashboardSummary();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            ps.setInt(3, year);
            ps.setInt(4, month);
            ps.setInt(5, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal totalIncome = nonNull(rs.getBigDecimal("total_income"));
                    BigDecimal totalExpense = nonNull(rs.getBigDecimal("total_expense"));
                    BigDecimal monthIncome = nonNull(rs.getBigDecimal("month_income"));
                    BigDecimal monthExpense = nonNull(rs.getBigDecimal("month_expense"));
                    summary.setTotalIncome(totalIncome);
                    summary.setTotalExpense(totalExpense);
                    summary.setBalance(totalIncome.subtract(totalExpense));
                    summary.setMonthIncome(monthIncome);
                    summary.setMonthExpense(monthExpense);
                    summary.setMonthBalance(monthIncome.subtract(monthExpense));
                    summary.setTransactionCount(rs.getInt("tx_count"));
                    summary.setIncomeCount(rs.getInt("income_count"));
                    summary.setExpenseCount(rs.getInt("expense_count"));
                }
            }
        }
        return summary;
    }

    public List<MonthlySummary> getMonthlySummaries(int userId, int year) throws SQLException {
        String sql = "SELECT MONTH(transaction_date) AS mo, "
                + "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS total_income, "
                + "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS total_expense, "
                + "COUNT(*) AS tx_count "
                + "FROM transactions "
                + "WHERE user_id = ? AND YEAR(transaction_date) = ? "
                + "GROUP BY MONTH(transaction_date) "
                + "ORDER BY mo ASC";

        Map<Integer, MonthlySummary> byMonth = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            MonthlySummary ms = new MonthlySummary();
            ms.setYear(year);
            ms.setMonth(m);
            ms.setMonthLabel(Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            byMonth.put(m, ms);
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, year);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int month = rs.getInt("mo");
                    MonthlySummary ms = byMonth.get(month);
                    if (ms != null) {
                        BigDecimal inc = nonNull(rs.getBigDecimal("total_income"));
                        BigDecimal exp = nonNull(rs.getBigDecimal("total_expense"));
                        ms.setIncome(inc);
                        ms.setExpense(exp);
                        ms.setNet(inc.subtract(exp));
                        ms.setTransactionCount(rs.getInt("tx_count"));
                    }
                }
            }
        }
        return new ArrayList<>(byMonth.values());
    }

    public List<Integer> getAvailableYears(int userId) throws SQLException {
        String sql = "SELECT DISTINCT YEAR(transaction_date) AS yr FROM transactions WHERE user_id = ? ORDER BY yr DESC";
        List<Integer> years = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    years.add(rs.getInt("yr"));
                }
            }
        }
        return years;
    }

    public List<CategorySummary> getCategoryBreakdown(int userId, Integer year, Integer month, String type)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT c.id AS category_id, c.name AS category_name, c.color AS category_color, "
                        + "COALESCE(SUM(t.amount), 0) AS total_amount, COUNT(t.id) AS tx_count "
                        + "FROM transactions t "
                        + "INNER JOIN categories c ON t.category_id = c.id "
                        + "WHERE t.user_id = ? AND t.type = ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(type);

        if (year != null) {
            sql.append(" AND YEAR(t.transaction_date) = ?");
            params.add(year);
        }
        if (month != null) {
            sql.append(" AND MONTH(t.transaction_date) = ?");
            params.add(month);
        }
        sql.append(" GROUP BY c.id, c.name, c.color ORDER BY total_amount DESC");

        List<CategorySummary> list = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CategorySummary item = new CategorySummary();
                    item.setCategoryId(rs.getInt("category_id"));
                    item.setCategoryName(rs.getString("category_name"));
                    item.setColor(rs.getString("category_color"));
                    BigDecimal amount = nonNull(rs.getBigDecimal("total_amount"));
                    item.setTotalAmount(amount);
                    item.setTransactionCount(rs.getInt("tx_count"));
                    grandTotal = grandTotal.add(amount);
                    list.add(item);
                }
            }
        }

        if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
            for (CategorySummary item : list) {
                double pct = item.getTotalAmount()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(grandTotal, 2, RoundingMode.HALF_UP)
                        .doubleValue();
                item.setPercentage(pct);
            }
        }
        return list;
    }

    private String buildWhereClause(TransactionFilter filter, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE t.user_id = ?");
        params.add(filter.getUserId());

        if (filter.getType() != null) {
            where.append(" AND t.type = ?");
            params.add(filter.getType());
        }
        if (filter.getCategoryId() != null) {
            where.append(" AND t.category_id = ?");
            params.add(filter.getCategoryId());
        }
        if (filter.getFromDate() != null) {
            where.append(" AND t.transaction_date >= ?");
            params.add(Date.valueOf(filter.getFromDate()));
        }
        if (filter.getToDate() != null) {
            where.append(" AND t.transaction_date <= ?");
            params.add(Date.valueOf(filter.getToDate()));
        }
        if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            String escaped = escapeLike(filter.getSearch().trim().toLowerCase());
            String pattern = "%" + escaped + "%";
            where.append(" AND (LOWER(t.description) LIKE ? ESCAPE '!' "
                    + "OR LOWER(COALESCE(t.notes, '')) LIKE ? ESCAPE '!' "
                    + "OR LOWER(c.name) LIKE ? ESCAPE '!')");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        return where.toString();
    }

    private void bindParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object val = params.get(i);
            int idx = i + 1;
            if (val instanceof Integer n) {
                ps.setInt(idx, n);
            } else if (val instanceof String s) {
                ps.setString(idx, s);
            } else if (val instanceof Date d) {
                ps.setDate(idx, d);
            } else if (val instanceof BigDecimal bd) {
                ps.setBigDecimal(idx, bd);
            } else {
                ps.setObject(idx, val);
            }
        }
    }

    private String escapeLike(String input) {
        return input.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        Transaction tx = new Transaction();
        tx.setId(rs.getInt("id"));
        tx.setUserId(rs.getInt("user_id"));
        tx.setCategoryId(rs.getInt("category_id"));
        tx.setCategoryName(rs.getString("category_name"));
        tx.setCategoryColor(rs.getString("category_color"));
        tx.setCategoryIcon(rs.getString("category_icon"));
        tx.setType(rs.getString("type"));
        tx.setAmount(rs.getBigDecimal("amount"));
        tx.setDescription(rs.getString("description"));
        tx.setNotes(rs.getString("notes"));
        Date txDate = rs.getDate("transaction_date");
        if (txDate != null) {
            tx.setTransactionDate(txDate.toLocalDate());
        }
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            tx.setCreatedAt(created.toInstant());
        }
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            tx.setUpdatedAt(updated.toInstant());
        }
        return tx;
    }
}

