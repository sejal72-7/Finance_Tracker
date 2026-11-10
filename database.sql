-- ============================================================
-- Personal Finance Tracker (FlowLedger) - MySQL 8.0 Schema
-- ============================================================

CREATE DATABASE IF NOT EXISTS finance_tracker
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE finance_tracker;

-- 1. Users table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. Categories table (user_id NULL = built-in system category)
CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    name VARCHAR(60) NOT NULL,
    type ENUM('INCOME', 'EXPENSE') NOT NULL,
    color VARCHAR(20) NOT NULL DEFAULT '#64748B',
    icon VARCHAR(40) NOT NULL DEFAULT 'tag',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_categories_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_categories_user_type (user_id, type)
) ENGINE=InnoDB;

-- 3. Transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    category_id INT NOT NULL,
    type ENUM('INCOME', 'EXPENSE') NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    description VARCHAR(200) NOT NULL,
    notes VARCHAR(500) NULL,
    transaction_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_category
        FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    INDEX idx_transactions_user_date (user_id, transaction_date DESC),
    INDEX idx_transactions_user_type (user_id, type),
    INDEX idx_transactions_user_cat (user_id, category_id)
) ENGINE=InnoDB;

-- ============================================================
-- Default Categories Seed (idempotent)
-- ============================================================

INSERT INTO categories (user_id, name, type, color, icon)
SELECT NULL, seed.name, seed.type, seed.color, seed.icon
FROM (
    SELECT 'Food' AS name, 'EXPENSE' AS type, '#F97316' AS color, 'utensils' AS icon
    UNION ALL SELECT 'Shopping', 'EXPENSE', '#EC4899', 'shopping-bag'
    UNION ALL SELECT 'Travel', 'EXPENSE', '#0EA5E9', 'plane'
    UNION ALL SELECT 'Education', 'EXPENSE', '#8B5CF6', 'book'
    UNION ALL SELECT 'Bills', 'EXPENSE', '#EAB308', 'receipt'
    UNION ALL SELECT 'Health', 'EXPENSE', '#EF4444', 'heart'
    UNION ALL SELECT 'Entertainment', 'EXPENSE', '#6366F1', 'film'
    UNION ALL SELECT 'Housing', 'EXPENSE', '#14B8A6', 'home'
    UNION ALL SELECT 'Other Expense', 'EXPENSE', '#64748B', 'tag'
    UNION ALL SELECT 'Salary', 'INCOME', '#10B981', 'briefcase'
    UNION ALL SELECT 'Freelance', 'INCOME', '#06B6D4', 'laptop'
    UNION ALL SELECT 'Investments', 'INCOME', '#3B82F6', 'trending-up'
    UNION ALL SELECT 'Gift', 'INCOME', '#A855F7', 'gift'
    UNION ALL SELECT 'Other Income', 'INCOME', '#22C55E', 'wallet'
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id IS NULL AND c.name = seed.name AND c.type = seed.type
);

