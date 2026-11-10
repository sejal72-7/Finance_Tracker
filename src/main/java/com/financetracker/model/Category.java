package com.financetracker.model;

/**
 * Represents an income or expense category (built-in when userId is null, or user-owned).
 */
public class Category {
    private int id;
    private Integer userId;
    private String name;
    private String type; // "INCOME" or "EXPENSE"
    private String color;
    private String icon;

    public Category() {
    }

    public Category(int id, Integer userId, String name, String type, String color, String icon) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.color = color;
        this.icon = icon;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public boolean isSystemDefault() {
        return userId == null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}

