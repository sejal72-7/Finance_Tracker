package com.financetracker.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Initializes the JDBC connection pool when Tomcat deploys the application
 * and shuts down pooled connections and JDBC cleanup threads on undeploy.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        DBConnection.init();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        DBConnection.shutdown();
    }
}

