package com.example.panel.service;

import org.springframework.dao.DataAccessException;
import org.springframework.util.StringUtils;

public final class DialogDataAccessSupport {

    private DialogDataAccessSupport() {
    }

    public static String summarizeDataAccessException(DataAccessException ex) {
        Throwable mostSpecificCause = ex.getMostSpecificCause();
        if (mostSpecificCause != null && StringUtils.hasText(mostSpecificCause.getMessage())) {
            return singleLine(mostSpecificCause.getMessage());
        }
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return ex.getClass().getSimpleName();
        }
        int sqlIndex = message.indexOf(" for SQL [");
        if (sqlIndex >= 0) {
            message = message.substring(0, sqlIndex);
        }
        return singleLine(message);
    }

    private static String singleLine(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
