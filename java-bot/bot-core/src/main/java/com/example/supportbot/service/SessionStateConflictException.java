package com.example.supportbot.service;

public class SessionStateConflictException extends RuntimeException {

    public SessionStateConflictException(String message) {
        super(message);
    }
}
