package com.albaro.rag.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class RagResponse {

    private String answer;
    private List<RagSource> sources;
    private RagMetrics metrics;
    // El contexto exacto que recibió el LLM. No se envía en el JSON de la API,
    // pero lo usa el juez para evaluar con lo mismo que vio el generador.
    @JsonIgnore
    private String context;
}