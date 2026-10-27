package com.albaro.rag.demo.intent;


public enum IntentCategory {
    consulta,
    reclamo,
    bloqueo,
    fuera_de_tema;


    public static IntentCategory fromModel(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("La categoría del modelo está vacía");
        }
        return IntentCategory.valueOf(value.strip().toLowerCase(java.util.Locale.ROOT));
    }
}
