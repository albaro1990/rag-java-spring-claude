package com.albaro.rag.demo.eval;

import java.util.List;

// Una conversación completa: los mensajes se envían en orden con el mismo sessionId.
public record ChatEvalConversation(
        String id,
        String split,
        List<ChatEvalTurn> turns
) {
}
