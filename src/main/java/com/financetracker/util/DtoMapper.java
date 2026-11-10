package com.financetracker.util;

import com.financetracker.model.Category;
import com.financetracker.model.CategorySummary;
import com.financetracker.model.DashboardSummary;
import com.financetracker.model.MonthlySummary;
import com.financetracker.model.Transaction;
import com.financetracker.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts domain model objects into JSON-serializable Maps.
 */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("fullName", user.getFullName());
        map.put("email", user.getEmail());
        map.put("createdAt", user.getCreatedAt());
        return map;
    }

    public static Map<String, Object> toCategoryMap(Category cat) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", cat.getId());
        map.put("name", cat.getName());
        map.put("type", cat.getType());
        map.put("color", cat.getColor());
        map.put("icon", cat.getIcon());
        map.put("systemDefault", cat.isSystemDefault());
        return map;
    }

    public static List<Map<String, Object>> toCategoryList(List<Category> list) {
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Category c : list) {
            out.add(toCategoryMap(c));
        }
        return out;
    }

    public static Map<String, Object> toTransactionMap(Transaction tx) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tx.getId());
        map.put("categoryId", tx.getCategoryId());
        map.put("categoryName", tx.getCategoryName());
        map.put("categoryColor", tx.getCategoryColor());
        map.put("categoryIcon", tx.getCategoryIcon());
        map.put("type", tx.getType());
        map.put("amount", tx.getAmount());
        map.put("description", tx.getDescription());
        map.put("notes", tx.getNotes() != null ? tx.getNotes() : "");
        map.put("transactionDate", tx.getTransactionDate());
        map.put("createdAt", tx.getCreatedAt());
        map.put("updatedAt", tx.getUpdatedAt());
        return map;
    }

    public static List<Map<String, Object>> toTransactionList(List<Transaction> list) {
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Transaction tx : list) {
            out.add(toTransactionMap(tx));
        }
        return out;
    }

    public static Map<String, Object> toDashboardSummaryMap(DashboardSummary s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalIncome", s.getTotalIncome());
        map.put("totalExpense", s.getTotalExpense());
        map.put("balance", s.getBalance());
        map.put("monthIncome", s.getMonthIncome());
        map.put("monthExpense", s.getMonthExpense());
        map.put("monthBalance", s.getMonthBalance());
        map.put("transactionCount", s.getTransactionCount());
        map.put("incomeCount", s.getIncomeCount());
        map.put("expenseCount", s.getExpenseCount());
        return map;
    }

    public static List<Map<String, Object>> toMonthlySummaryList(List<MonthlySummary> list) {
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (MonthlySummary m : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("year", m.getYear());
            map.put("month", m.getMonth());
            map.put("monthLabel", m.getMonthLabel());
            map.put("income", m.getIncome());
            map.put("expense", m.getExpense());
            map.put("net", m.getNet());
            map.put("transactionCount", m.getTransactionCount());
            out.add(map);
        }
        return out;
    }

    public static List<Map<String, Object>> toCategorySummaryList(List<CategorySummary> list) {
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (CategorySummary c : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("categoryId", c.getCategoryId());
            map.put("categoryName", c.getCategoryName());
            map.put("color", c.getColor());
            map.put("totalAmount", c.getTotalAmount());
            map.put("transactionCount", c.getTransactionCount());
            map.put("percentage", c.getPercentage());
            out.add(map);
        }
        return out;
    }
}

