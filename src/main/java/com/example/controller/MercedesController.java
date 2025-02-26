package com.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MercedesController {
    @GetMapping(value = {"/", ""})
    public String hello() {
        System.out.println("Mercedes endpoint hit!");
        return "Hello from Mercedes!";
    }
}