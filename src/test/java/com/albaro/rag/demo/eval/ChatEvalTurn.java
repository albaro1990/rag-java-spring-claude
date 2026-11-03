package com.albaro.rag.demo.eval;

import java.util.List;

// Un mensaje de una conversación de evaluación y lo que esperamos de él.
//
// - type: primer_turno | dependiente | autonoma | cambio_tema | fuera_de_tema
// - shouldAnswer: true si los documentos tienen la respuesta, false si debe abstenerse
// - expectedSources: documentos donde debe estar la evidencia (cualquiera sirve)
// - forbiddenSources: documentos que NO pueden aparecer entre las fuentes (evidencia
//   del documento equivocado, típica de una pregunta ambigua sin historial)
// - mustContain: grupos de palabras; la pregunta condensada debe tener AL MENOS UNA
//   palabra de CADA grupo (sin importar tildes ni mayúsculas)
// - mustNotContain: palabras que la pregunta condensada NO puede tener (contaminación)
public record ChatEvalTurn(
        String message,
        String type,
        boolean shouldAnswer,
        List<String> expectedSources,
        List<String> forbiddenSources,
        List<List<String>> mustContain,
        List<String> mustNotContain,
        String note
) {
}
