package com.example.controller;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Controller
public class MercedesController {
    private static final Logger LOGGER = Logger.getLogger(MercedesController.class.getName());
    private final OpenAiService openAiService;
    private final OkHttpClient pineconeClient;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public MercedesController(@Value("${OPENAI_API_KEY}") String openAiKey,
                              @Value("${PINECONE_API_KEY}") String pineconeKey) {
        LOGGER.info("Initializing with OPENAI_API_KEY: " + (openAiKey != null ? "set" : "null"));
        LOGGER.info("Initializing with PINECONE_API_KEY: " + (pineconeKey != null ? "set" : "null"));
        this.openAiService = new OpenAiService(openAiKey);
        this.pineconeClient = new OkHttpClient();
    }

    @GetMapping("/")
    public String home() {
        LOGGER.info("Home endpoint hit!");
        return "index";
    }

    @PostMapping("/query")
    public String query(@RequestParam("query") String query, Model model) {
        LOGGER.info("Query endpoint hit with: " + query);
        model.addAttribute("query", query);
        try {
            // Embed query with OpenAI
            EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                    .input(Collections.singletonList(query))
                    .model("text-embedding-ada-002")
                    .build();
            List<Double> embedding = openAiService.createEmbeddings(embeddingRequest)
                    .getData().get(0).getEmbedding();
            LOGGER.info("OpenAI embedding created");

            // Query Pinecone via REST API
            String pineconeJson = String.format(
                    "{\"vector\": %s, \"topK\": 1, \"includeMetadata\": true}",
                    embedding.toString()
            );
            RequestBody body = RequestBody.create(pineconeJson, JSON);
            Request pineconeRequest = new Request.Builder()
                    .url("https://novel-data-gu87d5x.svc.aped-4627-b74a.pinecone.io") // Replace with your Pinecone index URL
                    .header("Api-Key", System.getenv("PINECONE_API_KEY"))
                    .post(body)
                    .build();
            String retrievedText = "";
            String imagePath = "";
            try (Response response = pineconeClient.newCall(pineconeRequest).execute()) {
                String jsonResponse = response.body().string();
                LOGGER.info("Pinecone response: " + jsonResponse);
                if (jsonResponse.contains("\"matches\":[")) {
                    retrievedText = extractValue(jsonResponse, "\"text\":", ",");
                    imagePath = extractValue(jsonResponse, "\"image\":", "\"");
                }
            }

            // Query OpenAI with retrieved text
            ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                    .model("gpt-3.5-turbo")
                    .messages(List.of(
                            new ChatMessage("system", "You are a helpful assistant summarizing a Mercedes-Benz user manual."),
                            new ChatMessage("user", "Based on this: " + retrievedText + ", answer: " + query)
                    ))
                    .maxTokens(150)
                    .build();
            String answer = openAiService.createChatCompletion(chatRequest)
                    .getChoices().get(0).getMessage().getContent();
            LOGGER.info("OpenAI answer: " + answer);

            model.addAttribute("text", retrievedText.isEmpty() ? "No match found" : retrievedText);
            model.addAttribute("answer", answer);
            model.addAttribute("image", imagePath.isEmpty() ? "" : imagePath);
        } catch (Exception e) {
            LOGGER.severe("Error in query: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("text", "Error occurred");
            model.addAttribute("answer", "Sorry, something went wrong: " + e.getMessage());
            model.addAttribute("image", "");
        }
        return "result";
    }

    private String extractValue(String json, String key, String end) {
        int start = json.indexOf(key) + key.length() + 1;
        int finish = json.indexOf(end, start);
        return start > key.length() && finish > start ? json.substring(start, finish) : "";
    }
}