package com.financetracker.model;

import java.math.BigDecimal;

/**
 * Aggregated totals shown on the main dashboard KPI cards.
 */
public class DashboardSummary {
    private BigDecimal totalIncome = BigDecimal.ZERO;
    private BigDecimal totalExpense = BigDecimal.ZERO;
    private BigDecimal balance = BigDecimal.ZERO;
    private BigDecimal monthIncome = BigDecimal.ZERO;
    private BigDecimal monthExpense = BigDecimal.ZERO;
    private BigDecimal monthBalance = BigDecimal.ZERO;
    private int transactionCount;
    private int incomeCount;
    private int expenseCount;

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome;
    }

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(BigDecimal totalExpense) {
        this.totalExpense = totalExpense;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getMonthIncome() {
        return monthIncome;
    }

    public void setMonthIncome(BigDecimal monthIncome) {
        this.monthIncome = monthIncome;
    }

    public BigDecimal getMonthExpense() {
        return monthExpense;
    }

    public void setMonthExpense(BigDecimal monthExpense) {
        this.monthExpense = monthExpense;
    }

    public BigDecimal getMonthBalance() {
        return monthBalance;
    }

    public void setMonthBalance(BigDecimal monthBalance) {
        this.monthBalance = monthBalance;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public int getIncomeCount() {
        return incomeCount;
    }

    public void setIncomeCount(int incomeCount) {
        this.incomeCount = incomeCount;
    }

    public int getExpenseCount() {
        return expenseCount;
    }

    public void setExpenseCount(int expenseCount) {
        this.expenseCount = expenseCount;
    }
}

