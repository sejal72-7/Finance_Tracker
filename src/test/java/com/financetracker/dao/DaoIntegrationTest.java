package com.financetracker.dao;

import com.financetracker.config.DBConnection;
import com.financetracker.model.Category;
import com.financetracker.model.CategorySummary;
import com.financetracker.model.DashboardSummary;
import com.financetracker.model.MonthlySummary;
import com.financetracker.model.Transaction;
import com.financetracker.model.TransactionFilter;
import com.financetracker.model.User;
import com.financetracker.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DaoIntegrationTest {

    private final UserDAO userDAO = new UserDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    @BeforeAll
    static void initDb() throws Exception {
        DBConnection.configure(
                "jdbc:h2:mem:financetracker_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                5
        );
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    full_name VARCHAR(100) NOT NULL,
                    email VARCHAR(150) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            st.execute("""
                CREATE TABLE categories (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NULL,
                    name VARCHAR(60) NOT NULL,
                    type VARCHAR(10) NOT NULL,
                    color VARCHAR(20) NOT NULL DEFAULT '#64748B',
                    icon VARCHAR(40) NOT NULL DEFAULT 'tag',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
            """);
            st.execute("""
                CREATE TABLE transactions (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    category_id INT NOT NULL,
                    type VARCHAR(10) NOT NULL,
                    amount DECIMAL(12, 2) NOT NULL,
                    description VARCHAR(200) NOT NULL,
                    notes VARCHAR(500) NULL,
                    transaction_date DATE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
                )
            """);
            st.execute("INSERT INTO categories (user_id, name, type, color, icon) VALUES (NULL, 'Food', 'EXPENSE', '#F97316', 'utensils')");
            st.execute("INSERT INTO categories (user_id, name, type, color, icon) VALUES (NULL, 'Shopping', 'EXPENSE', '#EC4899', 'shopping-bag')");
            st.execute("INSERT INTO categories (user_id, name, type, color, icon) VALUES (NULL, 'Travel', 'EXPENSE', '#0EA5E9', 'plane')");
            st.execute("INSERT INTO categories (user_id, name, type, color, icon) VALUES (NULL, 'Salary', 'INCOME', '#10B981', 'briefcase')");
        }
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DELETE FROM transactions");
            st.execute("DELETE FROM categories WHERE user_id IS NOT NULL");
            st.execute("DELETE FROM users");
        }
    }

    @AfterAll
    static void tearDown() {
        DBConnection.shutdown();
    }

    @Test
    void userCrudWorks() throws Exception {
        User u = new User();
        u.setFullName("Alex Morgan");
        u.setEmail("Alex@Example.com");
        u.setPasswordHash(PasswordUtil.hashPassword("Pass123!"));

        User created = userDAO.create(u);
        assertTrue(created.getId() > 0);
        assertEquals("alex@example.com", created.getEmail());

        User byEmail = userDAO.findByEmail("ALEX@example.com");
        assertNotNull(byEmail);
        assertEquals(created.getId(), byEmail.getId());

        assertTrue(userDAO.updateFullName(created.getId(), "Alex J. Morgan"));
        assertEquals("Alex J. Morgan", userDAO.findById(created.getId()).getFullName());

        assertTrue(userDAO.delete(created.getId()));
        assertNull(userDAO.findById(created.getId()));
    }

    @Test
    void categoryAndTransactionCrudFiltersAndAnalyticsWork() throws Exception {
        User userA = createUser("User A", "a@example.com");
        User userB = createUser("User B", "b@example.com");

        List<Category> catsA = categoryDAO.findAllForUser(userA.getId(), null);
        Category food = catsA.stream().filter(c -> "Food".equals(c.getName())).findFirst().orElseThrow();
        Category travel = catsA.stream().filter(c -> "Travel".equals(c.getName())).findFirst().orElseThrow();
        Category salary = catsA.stream().filter(c -> "Salary".equals(c.getName())).findFirst().orElseThrow();

        // Custom category CRUD + user isolation
        Category custom = new Category();
        custom.setUserId(userA.getId());
        custom.setName("Coffee 100%");
        custom.setType("EXPENSE");
        custom.setColor("#78350F");
        custom.setIcon("tag");
        custom = categoryDAO.create(custom);
        assertNotNull(categoryDAO.findByIdAccessible(custom.getId(), userA.getId()));
        assertNull(categoryDAO.findByIdAccessible(custom.getId(), userB.getId()));

        // Create transactions
        Transaction t1 = createTx(userA.getId(), salary.getId(), "INCOME", "5000.00", "Monthly Salary", LocalDate.of(2026, 11, 1));
        Transaction t2 = createTx(userA.getId(), food.getId(), "EXPENSE", "125.50", "Grocery Market", LocalDate.of(2026, 11, 5));
        Transaction t3 = createTx(userA.getId(), travel.getId(), "EXPENSE", "400.00", "Train Ticket 50% off", LocalDate.of(2026, 10, 20));
        createTx(userB.getId(), salary.getId(), "INCOME", "9999.00", "Other User Income", LocalDate.of(2026, 11, 5));

        // Ownership isolation on findById, update, delete
        assertNull(transactionDAO.findById(t2.getId(), userB.getId()));
        assertFalse(transactionDAO.delete(t2.getId(), userB.getId()));

        // Update t2
        t2.setAmount(new BigDecimal("150.00"));
        t2.setDescription("Organic Grocery Market");
        assertTrue(transactionDAO.update(t2));
        Transaction updated = transactionDAO.findById(t2.getId(), userA.getId());
        assertEquals(new BigDecimal("150.00"), updated.getAmount());
        assertEquals("Organic Grocery Market", updated.getDescription());

        // Search with wildcard character '%' escaped cleanly
        TransactionFilter searchFilter = new TransactionFilter();
        searchFilter.setUserId(userA.getId());
        searchFilter.setSearch("50%");
        List<Transaction> pctMatches = transactionDAO.findAll(searchFilter);
        assertEquals(1, pctMatches.size());
        assertEquals(t3.getId(), pctMatches.get(0).getId());

        // Filter by type, category & date range
        TransactionFilter dateFilter = new TransactionFilter();
        dateFilter.setUserId(userA.getId());
        dateFilter.setType("EXPENSE");
        dateFilter.setFromDate(LocalDate.of(2026, 11, 1));
        dateFilter.setToDate(LocalDate.of(2026, 11, 30));
        List<Transaction> novExpenses = transactionDAO.findAll(dateFilter);
        assertEquals(1, novExpenses.size());
        assertEquals(t2.getId(), novExpenses.get(0).getId());

        // Dashboard summary
        DashboardSummary summary = transactionDAO.getDashboardSummary(userA.getId(), 2026, 11);
        assertEquals(0, new BigDecimal("5000.00").compareTo(summary.getTotalIncome()));
        assertEquals(0, new BigDecimal("550.00").compareTo(summary.getTotalExpense()));
        assertEquals(0, new BigDecimal("4450.00").compareTo(summary.getBalance()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(summary.getMonthIncome()));
        assertEquals(0, new BigDecimal("150.00").compareTo(summary.getMonthExpense()));
        assertEquals(3, summary.getTransactionCount());

        // Monthly summaries
        List<MonthlySummary> monthly = transactionDAO.getMonthlySummaries(userA.getId(), 2026);
        assertEquals(12, monthly.size());
        MonthlySummary oct = monthly.get(9);
        MonthlySummary nov = monthly.get(10);
        assertEquals(0, new BigDecimal("400.00").compareTo(oct.getExpense()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(nov.getIncome()));
        assertEquals(0, new BigDecimal("150.00").compareTo(nov.getExpense()));

        // Category breakdown
        List<CategorySummary> breakdown = transactionDAO.getCategoryBreakdown(userA.getId(), 2026, null, "EXPENSE");
        assertEquals(2, breakdown.size());
        assertEquals("Travel", breakdown.get(0).getCategoryName());

        // Delete t1 and verify count decreases
        assertTrue(transactionDAO.delete(t1.getId(), userA.getId()));
        assertEquals(2, transactionDAO.getDashboardSummary(userA.getId(), 2026, 11).getTransactionCount());

        // Unused custom category can be updated and deleted
        custom.setName("Updated Custom");
        assertTrue(categoryDAO.updateCustom(custom));
        assertFalse(categoryDAO.isCategoryInUse(custom.getId(), userA.getId()));
        assertTrue(categoryDAO.deleteCustom(custom.getId(), userA.getId()));
    }

    private User createUser(String name, String email) throws Exception {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setPasswordHash(PasswordUtil.hashPassword("Password123"));
        return userDAO.create(u);
    }

    private Transaction createTx(int userId, int catId, String type, String amount, String desc, LocalDate date)
            throws Exception {
        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setCategoryId(catId);
        tx.setType(type);
        tx.setAmount(new BigDecimal(amount));
        tx.setDescription(desc);
        tx.setNotes("Test note");
        tx.setTransactionDate(date);
        return transactionDAO.create(tx);
    }
}

