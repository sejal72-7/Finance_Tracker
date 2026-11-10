package com.financetracker.servlet;

import com.financetracker.dao.TransactionDAO;
import com.financetracker.model.CategorySummary;
import com.financetracker.model.MonthlySummary;
import com.financetracker.util.DtoMapper;
import com.financetracker.util.JsonUtil;
import com.financetracker.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides monthly income/expense summaries and category breakdown reports for charts.
 */
@WebServlet("/api/reports/*")
public class ReportServlet extends BaseApiServlet {

    private final TransactionDAO transactionDAO = new TransactionDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return;
        }

        String pathInfo = req.getPathInfo();
        if ("/monthly".equals(pathInfo)) {
            handleMonthlyReport(req, resp, userId);
        } else if ("/categories".equals(pathInfo)) {
            handleCategoryReport(req, resp, userId);
        } else {
            JsonUtil.sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown report endpoint.");
        }
    }

    private void handleMonthlyReport(HttpServletRequest req, HttpServletResponse resp, int userId) throws IOException {
        int currentYear = LocalDate.now().getYear();
        Integer yearParam = ValidationUtil.parsePositiveInt(req.getParameter("year"));
        int year = yearParam != null ? yearParam : currentYear;

        try {
            List<MonthlySummary> months = transactionDAO.getMonthlySummaries(userId, year);
            List<Integer> availableYears = transactionDAO.getAvailableYears(userId);
            if (!availableYears.contains(currentYear)) {
                availableYears.add(0, currentYear);
            }

            BigDecimal yearlyIncome = BigDecimal.ZERO;
            BigDecimal yearlyExpense = BigDecimal.ZERO;
            for (MonthlySummary m : months) {
                yearlyIncome = yearlyIncome.add(m.getIncome());
                yearlyExpense = yearlyExpense.add(m.getExpense());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("year", year);
            result.put("availableYears", availableYears);
            result.put("months", DtoMapper.toMonthlySummaryList(months));
            result.put("yearlyIncome", yearlyIncome);
            result.put("yearlyExpense", yearlyExpense);
            result.put("yearlyNet", yearlyIncome.subtract(yearlyExpense));
            sendSuccess(resp, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }

    private void handleCategoryReport(HttpServletRequest req, HttpServletResponse resp, int userId) throws IOException {
        String rawType = ValidationUtil.trimToNull(req.getParameter("type"));
        String type = rawType != null ? ValidationUtil.normalizeType(rawType) : "EXPENSE";
        if (type == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Type must be INCOME or EXPENSE.");
            return;
        }

        Integer year = ValidationUtil.parsePositiveInt(req.getParameter("year"));
        Integer month = ValidationUtil.parsePositiveInt(req.getParameter("month"));
        if (month != null && (month < 1 || month > 12)) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Month must be between 1 and 12.");
            return;
        }
        if (month != null && year == null) {
            year = LocalDate.now().getYear();
        }

        try {
            List<CategorySummary> breakdown = transactionDAO.getCategoryBreakdown(userId, year, month, type);
            BigDecimal total = BigDecimal.ZERO;
            for (CategorySummary c : breakdown) {
                total = total.add(c.getTotalAmount());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", type);
            result.put("year", year);
            result.put("month", month);
            result.put("total", total);
            result.put("categories", DtoMapper.toCategorySummaryList(breakdown));
            sendSuccess(resp, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }
}

