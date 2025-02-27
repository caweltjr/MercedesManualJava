package com.example.controller;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
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
            // Validate query
            if (query == null || query.trim().isEmpty()) {
                LOGGER.warning("Empty or null query received");
                model.addAttribute("text", "No query provided");
                model.addAttribute("answer", "Please enter a valid question about your Mercedes-Benz.");
                return "result";
            }

            // Embed query with OpenAI
            EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                    .input(Collections.singletonList(query.trim()))
                    .model("text-embedding-ada-002")
                    .build();
            List<Double> embedding = openAiService.createEmbeddings(embeddingRequest)
                    .getData().get(0).getEmbedding();
            LOGGER.info("OpenAI embedding created: " + embedding.size() + " dimensions, first few: " + embedding.subList(0, Math.min(5, embedding.size())));

            // Query Pinecone via REST API
            JSONObject payload = new JSONObject();
            payload.put("vector", embedding);
            payload.put("topK", 5);
            payload.put("includeMetadata", true);
            payload.put("namespace", "");
            String pineconeJson = payload.toString();
            LOGGER.info("Pinecone query JSON: " + pineconeJson.substring(0, Math.min(200, pineconeJson.length())) + "...");

            RequestBody body = RequestBody.create(pineconeJson, JSON);
            Request pineconeRequest = new Request.Builder()
                    .url("https://novel-data-gu87d5x.svc.aped-4627-b74a.pinecone.io/query")
                    .header("Api-Key", System.getenv("PINECONE_API_KEY"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "python-requests/2.28.1")
                    .post(body)
                    .build();
            String retrievedText = "";
            String imagePath = "";
            try (Response response = pineconeClient.newCall(pineconeRequest).execute()) {
                int responseCode = response.code();
                String jsonResponse = response.body().string();
                LOGGER.info("Pinecone response code: " + responseCode);
                LOGGER.info("Pinecone response body: " + jsonResponse);
                if (!response.isSuccessful()) {
                    LOGGER.severe("Pinecone query failed with code: " + responseCode);
                }
                if (jsonResponse.contains("\"matches\":[")) {
                    JSONObject jsonObj = new JSONObject(jsonResponse);
                    JSONArray matches = jsonObj.getJSONArray("matches");
                    if (matches.length() > 0) {
                        JSONObject match = matches.getJSONObject(0);
                        JSONObject metadata = match.getJSONObject("metadata");
                        retrievedText = metadata.getString("text");
                        imagePath = metadata.optString("image", "");
                    }
                } else {
                    LOGGER.warning("No matches found in Pinecone response");
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
}