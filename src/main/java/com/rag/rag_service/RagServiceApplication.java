package com.rag.rag_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
		org.springframework.ai.autoconfigure.vectorstore.qdrant.QdrantVectorStoreAutoConfiguration.class
})
public class RagServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RagServiceApplication.class, args);
	}

}
