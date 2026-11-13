package br.com.letterroyale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LetterRoyaleApplication {
    public static void main(String[] args) {
        SpringApplication.run(LetterRoyaleApplication.class, args);
        System.out.println("🎬 LetterRoyale rodando em http://localhost:8080");
    }
}
