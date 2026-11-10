# FlowLedger — Personal Finance Tracker

A complete end-to-end **Personal Finance Tracker** web application built with **HTML, CSS, JavaScript**, **Core Java Servlets**, **JDBC (`PreparedStatement`)**, **MySQL**, and **Apache Tomcat 10.1+ / 11**.

---

## Features

1. **User Registration & Login** — Salted `PBKDF2WithHmacSHA256` password hashing (`PasswordUtil`) and `HttpSession`-based authentication (`AuthFilter`).
2. **Financial Dashboard** — Real-time **Current Balance**, **Total Income**, **Total Expenses**, **This Month Net**, and savings rate.
3. **Add Income & Expense Transactions** — Type-aware category selection, `BigDecimal` monetary precision, date, description, and optional notes.
4. **Categorized Transactions** — Pre-seeded MySQL categories (`Food`, `Shopping`, `Travel`, `Education`, `Bills`, `Health`, `Entertainment`, `Housing`, `Salary`, `Freelance`, `Investments`, `Gift`, etc.) plus full custom category CRUD.
5. **Complete Transaction History** — Paginated table with filtered summary totals and CSV export.
6. **Edit & Delete Transactions** — Ownership-verified updates and confirmation-protected deletion.
7. **Search & Multi-Criteria Filtering** — Parameterized JDBC `PreparedStatement` queries by keyword, type (`INCOME` / `EXPENSE`), category, date range, and quick presets (`This Month`, `Last 30 Days`, `This Year`).
8. **Monthly Income & Expense Summary** — Year selector, yearly totals, savings rate, and 12-month breakdown table.
9. **Interactive Expense Charts** — Zero-dependency HTML5 `<canvas>` Donut chart (Expense Breakdown by Category) and Grouped Bar chart (Monthly Income vs. Expenses) with hover tooltips.
10. **Secure Logout** — Invalidates server `HttpSession`, clears `JSESSIONID` cookie, and disables browser caching on protected views.

---

## Technology Stack

- **Frontend:** HTML5, CSS3, Vanilla JavaScript (Fetch API + HTML5 Canvas charts)
- **Backend:** Core Java 17+, Jakarta Servlets (`jakarta.servlet-api:6.0.0`), Servlet Filters & `ServletContextListener`
- **Database:** MySQL 8.0+
- **Connectivity:** JDBC (`com.mysql:mysql-connector-j:8.4.0`), `PreparedStatement`, thread-safe pooled `DBConnection`
- **Server:** Apache Tomcat 10.1.x or 11.0.x

---

## Project Structure

```text
├── database.sql                         # MySQL schema + default category seed data
├── pom.xml                              # Maven WAR build configuration
└── src
    ├── main
    │   ├── java/com/financetracker
    │   │   ├── config/                  # DBConnection pool + AppContextListener
    │   │   ├── dao/                     # UserDAO, CategoryDAO, TransactionDAO (PreparedStatement CRUD)
    │   │   ├── filter/                  # AuthFilter (UTF-8, cache-control, session guard)
    │   │   ├── model/                   # Domain POJOs & summary models
    │   │   ├── servlet/                 # Auth, Transaction, Category, Dashboard, Report, Health servlets
    │   │   └── util/                    # PasswordUtil, JsonUtil, ValidationUtil, DtoMapper
    │   ├── resources/
    │   │   └── db.properties.example    # Template for MySQL connection settings
    │   └── webapp/
    │       ├── WEB-INF/web.xml          # Servlet 6.0 descriptor & cookie session config
    │       ├── css/styles.css           # Responsive modern UI styles
    │       ├── js/                      # api.js, auth.js, charts.js, dashboard.js
    │       ├── index.html               # Sign In / Create Account portal
    │       ├── register.html            # Redirects to #register tab
    │       └── dashboard.html           # Main authenticated workspace
    └── test/java/com/financetracker/     # JUnit 5 unit & JDBC DAO integration tests
```

---

## Prerequisites

1. **Java Development Kit (JDK) 17 or 21**
2. **Apache Maven 3.8+**
3. **MySQL Server 8.0+** (running on port `3306`)
4. **Apache Tomcat 10.1.x** (or **Tomcat 11.0.x**) — uses `jakarta.servlet.*`

> **Note:** Do not use Tomcat 9 or older, as Tomcat 9 uses the legacy `javax.servlet.*` package while Tomcat 10.1+ uses `jakarta.servlet.*`.

---

## Setup & Run Instructions (Apache Tomcat)

### Step 1: Create the MySQL Database & Seed Categories

Run `database.sql` using MySQL CLI or MySQL Workbench:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p < database.sql
```

This creates the `finance_tracker` database, `users`, `categories`, and `transactions` tables, and inserts the default Income & Expense categories.

### Step 2: Configure MySQL Credentials

Copy `src/main/resources/db.properties.example` to `src/main/resources/db.properties` and set your MySQL username and password (`db.properties` is gitignored to protect your credentials):

```powershell
Copy-Item src\main\resources\db.properties.example src\main\resources\db.properties
```

```properties
db.url=jdbc:mysql://localhost:3306/finance_tracker?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.username=root
db.password=YOUR_MYSQL_PASSWORD
db.pool.maxSize=10
```

*(Alternatively, you can override credentials without editing files by setting `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables or passing `-Ddb.username=... -Ddb.password=...` in Tomcat `CATALINA_OPTS`.)*

### Step 3: Build the WAR File with Maven

From the project root (`d:\java_mini_pro`):

```powershell
mvn clean package
```

This runs all JUnit tests and builds `target\finance-tracker.war`.

### Step 4: Deploy to Apache Tomcat 10.1+

1. Download **Apache Tomcat 10.1.x** (64-bit Windows zip) from <https://tomcat.apache.org/download-10.cgi> and extract it (for example, to `D:\tools\apache-tomcat-10.1.xx`).
2. Copy the WAR into Tomcat's `webapps` folder:

   ```powershell
   Copy-Item target\finance-tracker.war D:\tools\apache-tomcat-10.1.xx\webapps\finance-tracker.war
   ```

3. Start Apache Tomcat:

   ```powershell
   cd D:\tools\apache-tomcat-10.1.xx\bin
   .\startup.bat
   ```

   *(Or run `.\catalina.bat run` to view live console logs in the terminal.)*

4. Open your browser and visit:

   **`http://localhost:8080/finance-tracker/`**

5. Verify health endpoint if needed:

   **`http://localhost:8080/finance-tracker/api/health`** -> should return `{"status":"UP","database":true}`.

6. To stop Tomcat later:

   ```powershell
   .\shutdown.bat
   ```

---

## API Endpoints Summary

| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/health` | `GET` | Checks Tomcat servlet + JDBC DB health |
| `/api/auth/register` | `POST` | Register a new user account |
| `/api/auth/login` | `POST` | Authenticate & create HTTP session |
| `/api/auth/logout` | `POST` / `GET` | Invalidate session & clear cookie |
| `/api/auth/me` | `GET` | Get current authenticated user |
| `/api/dashboard` | `GET` | KPIs, yearly monthly summaries, category breakdown |
| `/api/transactions` | `GET` | List, search & filter transactions (`type`, `categoryId`, `search`, `fromDate`, `toDate`, `page`, `size`) |
| `/api/transactions/{id}` | `GET` / `PUT` / `DELETE` | Read, update, or delete a single transaction |
| `/api/transactions` | `POST` | Add a new income or expense transaction |
| `/api/categories` | `GET` / `POST` | List categories or add custom category |
| `/api/categories/{id}` | `PUT` / `DELETE` | Update or delete user custom category |
| `/api/reports/monthly` | `GET` | 12-month summary for `?year=YYYY` |
| `/api/reports/categories` | `GET` | Category totals & percentages (`?type=EXPENSE&year=...&month=...`) |

