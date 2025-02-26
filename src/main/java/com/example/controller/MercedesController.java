package com.example.controller;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class MercedesController {
    private final OpenAiService openAiService;

    public MercedesController(@Value("${OPENAI_API_KEY}") String openAiKey) {
        this.openAiService = new OpenAiService(openAiKey);
    }

    @GetMapping(value = {"/", ""})
    public String home() {
        return "index";
    }

    @PostMapping("/")
    public String query(@RequestParam("query") String query, Model model) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(List.of(
                        new ChatMessage("system", "You are a helpful assistant summarizing a Mercedes-Benz user manual."),
                        new ChatMessage("user", query)
                ))
                .maxTokens(150)
                .build();
        String answer = openAiService.createChatCompletion(request)
                .getChoices().get(0).getMessage().getContent();

        model.addAttribute("query", query);
        model.addAttribute("text", "Placeholder text"); // Pinecone next
        model.addAttribute("answer", answer);
        model.addAttribute("image", "");
        return "result";
    }
}