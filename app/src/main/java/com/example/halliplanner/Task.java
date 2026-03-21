package com.example.halliplanner;

public class Task {

    private String title;
    private String description;
    private String status;

    public Task() {
        // Constructor vacío necesario para Firestore
    }

    public Task(String title, String description, String status) {
        this.title = title;
        this.description = description;
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }
}
