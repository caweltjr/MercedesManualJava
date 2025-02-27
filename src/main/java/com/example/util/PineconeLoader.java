package com.example.util;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.embedding.EmbeddingRequest;
import okhttp3.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class PineconeLoader {
    private static final Logger LOGGER = Logger.getLogger(PineconeLoader.class.getName());
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static void main(String[] args) throws Exception {
        String openAiKey = System.getenv("OPENAI_API_KEY");
        String pineconeKey = System.getenv("PINECONE_API_KEY");
        OpenAiService openAiService = new OpenAiService(openAiKey);
        OkHttpClient pineconeClient = new OkHttpClient();

        loadPdf("C:/Mercedes/23MercedesManual.pdf", "merc_", openAiService, pineconeClient, pineconeKey);
    }

    public static void loadPdf(String pdfPath, String prefix, OpenAiService openAiService, OkHttpClient pineconeClient, String pineconeKey) throws Exception {
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String allText = stripper.getText(document);
            String[] words = allText.split("\\s+");
            int chunkSize = 500;
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < words.length; i += chunkSize) {
                int end = Math.min(i + chunkSize, words.length);
                chunks.add(String.join(" ", Arrays.copyOfRange(words, i, end)));
            }

            List<String> vectors = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                EmbeddingRequest request = EmbeddingRequest.builder()
                        .input(Collections.singletonList(chunk))
                        .model("text-embedding-ada-002")
                        .build();
                List<Double> embedding = openAiService.createEmbeddings(request)
                        .getData().get(0).getEmbedding();

                String vectorJson = String.format(
                        "{\"id\": \"%s%d\", \"values\": %s, \"metadata\": {\"text\": \"%s\"}}",
                        prefix, i, embedding.toString(), chunk.replace("\"", "\\\"")
                );
                vectors.add(vectorJson);
            }

            String upsertJson = String.format("{\"vectors\": [%s]}", String.join(",", vectors));
            RequestBody body = RequestBody.create(upsertJson, JSON);
            Request upsertRequest = new Request.Builder()
                    .url("https://novel-data-gu87d5x.svc.aped-4627-b74a.pinecone.io") // Replace with your Pinecone index URL
                    .header("Api-Key", pineconeKey)
                    .post(body)
                    .build();
            try (Response response = pineconeClient.newCall(upsertRequest).execute()) {
                LOGGER.info("Pinecone upsert response: " + response.body().string());
            }
        }
    }
}