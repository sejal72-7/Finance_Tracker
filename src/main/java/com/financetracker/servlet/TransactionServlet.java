package com.financetracker.servlet;

import com.financetracker.dao.CategoryDAO;
import com.financetracker.dao.TransactionDAO;
import com.financetracker.model.Category;
import com.financetracker.model.Transaction;
import com.financetracker.model.TransactionFilter;
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
 * Handles CRUD operations, search, and multi-criteria filtering for user transactions.
 */
@WebServlet("/api/transactions/*")
public class TransactionServlet extends BaseApiServlet {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && !pathInfo.isBlank() && !"/".equals(pathInfo)) {
            handleGetSingle(req, resp, userId);
            return;
        }

        TransactionFilter filter = new TransactionFilter();
        filter.setUserId(userId);

        String rawType = ValidationUtil.trimToNull(req.getParameter("type"));
        if (rawType != null) {
            String normalized = ValidationUtil.normalizeType(rawType);
            if (normalized == null) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Type must be INCOME or EXPENSE.");
                return;
            }
            filter.setType(normalized);
        }

        String rawCatId = ValidationUtil.trimToNull(req.getParameter("categoryId"));
        if (rawCatId != null) {
            Integer catId = ValidationUtil.parsePositiveInt(rawCatId);
            if (catId == null) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid categoryId parameter.");
                return;
            }
            filter.setCategoryId(catId);
        }

        String rawFrom = ValidationUtil.trimToNull(req.getParameter("fromDate"));
        if (rawFrom != null) {
            LocalDate fromDate = ValidationUtil.parseDate(rawFrom);
            if (fromDate == null) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid fromDate format (use YYYY-MM-DD).");
                return;
            }
            filter.setFromDate(fromDate);
        }

        String rawTo = ValidationUtil.trimToNull(req.getParameter("toDate"));
        if (rawTo != null) {
            LocalDate toDate = ValidationUtil.parseDate(rawTo);
            if (toDate == null) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid toDate format (use YYYY-MM-DD).");
                return;
            }
            filter.setToDate(toDate);
        }

        if (filter.getFromDate() != null && filter.getToDate() != null
                && filter.getFromDate().isAfter(filter.getToDate())) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "From date cannot be after To date.");
            return;
        }

        filter.setSearch(ValidationUtil.trimToNull(req.getParameter("search")));

        Integer pageParam = ValidationUtil.parsePositiveInt(req.getParameter("page"));
        Integer sizeParam = ValidationUtil.parsePositiveInt(req.getParameter("size"));
        int page = pageParam != null ? pageParam : 1;
        int size = sizeParam != null ? Math.min(sizeParam, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        filter.setLimit(size);
        filter.setOffset((page - 1) * size);

        try {
            List<Transaction> transactions = transactionDAO.findAll(filter);
            TransactionDAO.FilteredTotals totals = transactionDAO.getFilteredTotals(filter);
            int totalPages = totals.count == 0 ? 1 : (int) Math.ceil((double) totals.count / size);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("transactions", DtoMapper.toTransactionList(transactions));
            result.put("totalCount", totals.count);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", totalPages);
            result.put("filteredIncome", totals.incomeTotal);
            result.put("filteredExpense", totals.expenseTotal);
            result.put("filteredNet", totals.incomeTotal.subtract(totals.expenseTotal));
            sendSuccess(resp, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }

    private void handleGetSingle(HttpServletRequest req, HttpServletResponse resp, int userId) throws IOException {
        Integer id = extractPathId(req);
        if (id == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Valid transaction ID is required.");
            return;
        }
        try {
            Transaction tx = transactionDAO.findById(id, userId);
            if (tx == null) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Transaction not found.");
                return;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("transaction", DtoMapper.toTransactionMap(tx));
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

        Transaction parsed = parseAndValidateBody(req, resp, userId);
        if (parsed == null) {
            return;
        }

        try {
            if (!validateCategoryOwnershipAndType(resp, userId, parsed)) {
                return;
            }
            Transaction created = transactionDAO.create(parsed);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("transaction", DtoMapper.toTransactionMap(created));
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
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Valid transaction ID is required.");
            return;
        }

        Transaction parsed = parseAndValidateBody(req, resp, userId);
        if (parsed == null) {
            return;
        }
        parsed.setId(id);

        try {
            Transaction existing = transactionDAO.findById(id, userId);
            if (existing == null) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Transaction not found.");
                return;
            }
            if (!validateCategoryOwnershipAndType(resp, userId, parsed)) {
                return;
            }

            transactionDAO.update(parsed);
            Transaction updated = transactionDAO.findById(id, userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("transaction", DtoMapper.toTransactionMap(updated));
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
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Valid transaction ID is required.");
            return;
        }

        try {
            boolean deleted = transactionDAO.delete(id, userId);
            if (!deleted) {
                JsonUtil.sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Transaction not found.");
                return;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "Transaction deleted successfully.");
            sendSuccess(resp, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            handleSqlException(req, resp, e);
        }
    }

    private Transaction parseAndValidateBody(HttpServletRequest req, HttpServletResponse resp, int userId)
            throws IOException {
        Map<String, Object> data;
        try {
            data = JsonUtil.readRequestData(req);
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON payload.");
            return null;
        }

        String type = ValidationUtil.normalizeType(JsonUtil.getString(data, "type"));
        Integer categoryId = JsonUtil.getInt(data, "categoryId");
        BigDecimal amount = ValidationUtil.parsePositiveAmount(data.get("amount"));
        String description = ValidationUtil.trimToNull(JsonUtil.getString(data, "description"));
        String notes = ValidationUtil.trimToNull(JsonUtil.getString(data, "notes"));
        LocalDate txDate = ValidationUtil.parseDate(JsonUtil.getString(data, "transactionDate"));

        if (type == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Transaction type must be INCOME or EXPENSE.");
            return null;
        }
        if (categoryId == null || categoryId <= 0) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Please select a valid category.");
            return null;
        }
        if (amount == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Amount must be greater than zero.");
            return null;
        }
        if (description == null || description.length() > 200) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Description is required and must be at most 200 characters.");
            return null;
        }
        if (notes != null && notes.length() > 500) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Notes must be at most 500 characters.");
            return null;
        }
        if (txDate == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Valid transaction date (YYYY-MM-DD) is required.");
            return null;
        }

        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setType(type);
        tx.setCategoryId(categoryId);
        tx.setAmount(amount);
        tx.setDescription(description);
        tx.setNotes(notes);
        tx.setTransactionDate(txDate);
        return tx;
    }

    private boolean validateCategoryOwnershipAndType(HttpServletResponse resp, int userId, Transaction tx)
            throws SQLException, IOException {
        Category category = categoryDAO.findByIdAccessible(tx.getCategoryId(), userId);
        if (category == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Selected category does not exist.");
            return false;
        }
        if (!category.getType().equals(tx.getType())) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Selected category does not match transaction type " + tx.getType() + ".");
            return false;
        }
        return true;
    }
}

