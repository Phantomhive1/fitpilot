package com.fitpilot.dto;

import jakarta.validation.constraints.NotBlank;

/** 聊天请求体 */
public record ChatRequest(
        @NotBlank String sessionId,
        @NotBlank String message) {
}
