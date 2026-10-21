package com.albaro.rag.demo.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;

public class EmbeddingDemo {

    public static void main(String[] args) {

        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();

        String texto = "Una tarjeta de crédito permite realizar compras utilizando una línea de crédito.";

        Embedding embedding = model.embed(texto).content();

        System.out.println("Dimensiones: " + embedding.dimension());
        System.out.println("Vector:");
        System.out.println(embedding.vectorAsList());
    }

}
