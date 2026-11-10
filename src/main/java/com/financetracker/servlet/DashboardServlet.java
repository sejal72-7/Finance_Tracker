package com.financetracker.servlet;

import com.financetracker.dao.TransactionDAO;
import com.financetracker.model.CategorySummary;
import com.financetracker.model.DashboardSummary;
import com.financetracker.model.MonthlySummary;
import com.financetracker.util.DtoMapper;
import com.financetracker.util.JsonUtil;
import com.financetracker.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates KPI totals, monthly trend series, and category breakdown for the dashboard.
 */
@WebServlet("/api/dashboard")
public class DashboardServlet extends BaseApiServlet {

    private final TransactionDAO transactionDAO = new TransactionDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return;
        }

        LocalDate today = LocalDate.now();
        Integer yearParam = ValidationUtil.parsePositiveInt(req.getParameter("year"));
        Integer monthParam = ValidationUtil.parsePositiveInt(req.getParameter("month"));
        int year = yearParam != null ? yearParam : today.getYear();
        int month = (monthParam != null && monthParam >= 1 && monthParam <= 12) ? monthParam : today.getMonthValue();
        String chartPeriod = ValidationUtil.trimToNull(req.getParameter("period"));
        boolean monthScoped = "month".equalsIgnoreCase(chartPeriod);

        try {
            DashboardSummary summary = transactionDAO.getDashboardSummary(userId, today.getYear(), today.getMonthValue());
            List<MonthlySummary> monthlySummaries = transactionDAO.getMonthlySummaries(userId, year);
            List<Integer> availableYears = transactionDAO.getAvailableYears(userId);
            if (!availableYears.contains(today.getYear())) {
                availableYears.add(0, today.getYear());
            }
            if (!availableYears.contains(year)) {
                availableYears.add(year);
            }

            List<CategorySummary> expenseBreakdown = transactionDAO.getCategoryBreakdown(
                    userId,
                    monthScoped ? year : null,
                    monthScoped ? month : null,
                    "EXPENSE"
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("summary", DtoMapper.toDashboardSummaryMap(summary));
            result.put("year", year);
            result.put("month", month);
            result.put("currentYear", today.getYear());
            result.put("currentMonth", today.getMonthValue());
            result.put("availableYears", availableYears);
            result.put("monthlySummaries", DtoMapper.toMonthlySummaryList(monthlySummaries));
            result.put("expenseBreakdown", DtoMapper.toCategorySummaryList(expenseBreakdown));
            sendSuccess(resp, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }
}

