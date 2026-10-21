package com.albaro.rag.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagSource {

    private String source;
    private Integer chunkIndex;
    private double distance;
}