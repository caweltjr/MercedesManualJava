package com.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MercedesController {
    @GetMapping(value = {"/", ""})
    public String home() {
        return "index"; // Renders index.html
    }

    @PostMapping("/")
    public String query(@RequestParam("query") String query, Model model) {
        model.addAttribute("query", query);
        model.addAttribute("text", "Placeholder text"); // Pinecone later
        model.addAttribute("answer", "Placeholder answer"); // OpenAI later
        model.addAttribute("image", ""); // PDF image later
        return "result"; // Renders result.html
    }
}