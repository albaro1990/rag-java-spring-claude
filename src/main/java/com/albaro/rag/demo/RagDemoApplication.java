package com.albaro.rag.demo;

import com.albaro.rag.demo.config.OllamaProperties;
import com.albaro.rag.demo.config.PromptProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({OllamaProperties.class,  PromptProperties.class})
public class RagDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(RagDemoApplication.class, args);
	}

}
