package com.albaro.rag.demo.eval;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// Lo que el juez debe devolver. El SDK convierte este record
// en un JSON Schema, y Claude está obligado a responder con ese formato.
public record JudgeVerdict(

        @JsonPropertyDescription("1 a 5: ¿cada afirmación está respaldada por el CONTEXTO? 5 = todo respaldado, 1 = inventa información importante")
        int fidelidad,

        @JsonPropertyDescription("1 a 5: ¿cubre los puntos de la RESPUESTA ESPERADA? 5 = todos, 1 = ninguno")
        int completitud,

        @JsonPropertyDescription("1 a 5: ¿responde lo preguntado sin agregar información innecesaria? 5 = directa, 1 = mayormente irrelevante")
        int relevancia,

        @JsonPropertyDescription("Una o dos frases que expliquen los puntajes, mencionando cualquier afirmación no respaldada o punto faltante")
        String justificacion
) {
}
