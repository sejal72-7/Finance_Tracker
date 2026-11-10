package com.financetracker.config;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central JDBC connection manager with lightweight thread-safe connection pooling.
 * Configuration priority:
 * 1. Explicit configure(...) call (used in tests)
 * 2. JVM system properties (-Ddb.url, -Ddb.username, -Ddb.password, -Ddb.pool.maxSize)
 * 3. Environment variables (DB_URL, DB_USERNAME, DB_PASSWORD, DB_POOL_MAX_SIZE)
 * 4. Classpath db.properties
 */
public final class DBConnection {

    private static final int DEFAULT_MAX_POOL_SIZE = 10;
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static int maxPoolSize = DEFAULT_MAX_POOL_SIZE;

    private static BlockingQueue<Connection> pool;
    private static final List<Connection> allCreatedConnections = new ArrayList<>();
    private static boolean initialized = false;

    private DBConnection() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        Properties props = loadPropertiesFromClasspath();
        jdbcUrl = firstNonBlank(
                System.getProperty("db.url"),
                System.getenv("DB_URL"),
                props.getProperty("db.url")
        );
        username = firstNonBlank(
                System.getProperty("db.username"),
                System.getenv("DB_USERNAME"),
                props.getProperty("db.username")
        );
        String sysPass = System.getProperty("db.password");
        String envPass = System.getenv("DB_PASSWORD");
        password = sysPass != null ? sysPass : (envPass != null ? envPass : props.getProperty("db.password", ""));

        String poolSizeStr = firstNonBlank(
                System.getProperty("db.pool.maxSize"),
                System.getenv("DB_POOL_MAX_SIZE"),
                props.getProperty("db.pool.maxSize")
        );
        maxPoolSize = parsePoolSize(poolSizeStr);
        pool = new ArrayBlockingQueue<>(maxPoolSize);
        loadDriverForUrl(jdbcUrl);
        initialized = true;
    }

    public static synchronized void configure(String url, String user, String pass, int poolSize) {
        shutdownPoolOnly();
        jdbcUrl = url;
        username = user;
        password = pass != null ? pass : "";
        maxPoolSize = Math.max(1, poolSize);
        pool = new ArrayBlockingQueue<>(maxPoolSize);
        loadDriverForUrl(jdbcUrl);
        initialized = true;
    }

    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            init();
        }
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new SQLException("Database URL is not configured.");
        }

        // Try reusing a healthy pooled connection first
        Connection pooled;
        while ((pooled = pool.poll()) != null) {
            if (isConnectionHealthy(pooled)) {
                return wrapConnection(pooled);
            }
            closeQuietly(pooled);
        }

        // Otherwise create a new physical JDBC connection
        Connection physical = DriverManager.getConnection(jdbcUrl, username, password);
        synchronized (allCreatedConnections) {
            allCreatedConnections.add(physical);
        }
        return wrapConnection(physical);
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public static synchronized void shutdown() {
        shutdownPoolOnly();
        deregisterAppDrivers();
    }

    private static synchronized void shutdownPoolOnly() {
        if (pool != null) {
            pool.clear();
        }
        synchronized (allCreatedConnections) {
            for (Connection conn : allCreatedConnections) {
                closeQuietly(conn);
            }
            allCreatedConnections.clear();
        }
        initialized = false;
    }

    private static Connection wrapConnection(Connection physical) {
        AtomicBoolean closed = new AtomicBoolean(false);
        return (Connection) Proxy.newProxyInstance(
                DBConnection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("close".equals(name)) {
                        if (closed.compareAndSet(false, true)) {
                            releaseConnection(physical);
                        }
                        return null;
                    }
                    if ("isClosed".equals(name)) {
                        return closed.get() || physical.isClosed();
                    }
                    if (closed.get()) {
                        throw new SQLException("Connection has already been closed.");
                    }
                    try {
                        return method.invoke(physical, args);
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        throw ite.getTargetException();
                    }
                }
        );
    }

    private static void releaseConnection(Connection physical) {
        if (!initialized || physical == null) {
            closeQuietly(physical);
            return;
        }
        try {
            if (physical.isClosed()) {
                removeTrackedConnection(physical);
                return;
            }
            if (!physical.getAutoCommit()) {
                try {
                    physical.rollback();
                } catch (SQLException ignored) {
                    closeQuietly(physical);
                    return;
                }
                physical.setAutoCommit(true);
            }
            if (!pool.offer(physical, 10, TimeUnit.MILLISECONDS)) {
                closeQuietly(physical);
            }
        } catch (SQLException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            closeQuietly(physical);
        }
    }

    private static boolean isConnectionHealthy(Connection conn) {
        try {
            return !conn.isClosed() && conn.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void closeQuietly(Connection conn) {
        if (conn != null) {
            removeTrackedConnection(conn);
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private static void removeTrackedConnection(Connection conn) {
        synchronized (allCreatedConnections) {
            allCreatedConnections.remove(conn);
        }
    }

    private static Properties loadPropertiesFromClasspath() {
        Properties props = new Properties();
        try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }
        return props;
    }

    private static void loadDriverForUrl(String url) {
        if (url == null) {
            return;
        }
        try {
            if (url.startsWith("jdbc:mysql:")) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } else if (url.startsWith("jdbc:h2:")) {
                Class.forName("org.h2.Driver");
            }
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static void deregisterAppDrivers() {
        ClassLoader cl = DBConnection.class.getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == cl) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException ignored) {
                }
            }
        }
        try {
            Class<?> cleanupClass = Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread");
            cleanupClass.getMethod("checkedShutdown").invoke(null);
        } catch (Exception ignored) {
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static int parsePoolSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_POOL_SIZE;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.max(1, Math.min(parsed, 50));
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_POOL_SIZE;
        }
    }
}

