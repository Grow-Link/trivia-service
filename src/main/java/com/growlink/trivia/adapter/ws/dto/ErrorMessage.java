package com.growlink.trivia.adapter.ws.dto;

public record ErrorMessage(String type, String message) {
    public static ErrorMessage of(String message) {
        return new ErrorMessage("ERROR", message);
    }
}
