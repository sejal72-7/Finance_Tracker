package com.financetracker.model;

import java.time.LocalDate;

/**
 * Filter criteria used when searching and filtering transactions.
 */
public class TransactionFilter {
    private int userId;
    private String type; // optional: "INCOME" or "EXPENSE"
    private Integer categoryId; // optional
    private String search; // optional keyword matching description, notes, or category name
    private LocalDate fromDate; // optional inclusive
    private LocalDate toDate; // optional inclusive
    private int limit = 50;
    private int offset = 0;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}

