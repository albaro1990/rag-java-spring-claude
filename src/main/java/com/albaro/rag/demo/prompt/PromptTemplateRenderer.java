package com.albaro.rag.demo.prompt;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PromptTemplateRenderer {

    public String render(String template, Map<String, String> variables) {

        String result = template;

        for (Map.Entry<String, String> entry : variables.entrySet()) {

            String placeholder = "{" + entry.getKey() + "}";

            result = result.replace(
                    placeholder,
                    entry.getValue()
            );
        }

        return result;
    }
}