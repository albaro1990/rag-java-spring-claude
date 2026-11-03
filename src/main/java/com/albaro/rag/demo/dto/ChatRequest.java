package com.albaro.rag.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(

        @NotBlank(message = "sessionId es obligatorio")
        @Size(max = 100, message = "sessionId no puede superar 100 caracteres")
        String sessionId,

        @Size(max = 2000)
        @NotBlank(message = "message es obligatorio")
        String message

) {
}