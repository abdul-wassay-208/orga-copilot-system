package com.example.authapi.chat;

import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RestTemplate restTemplate;

    @Value("${chatbot.base-url:http://localhost:8000}")
    private String chatbotBaseUrl;

    public ChatController() {
        this.restTemplate = new RestTemplate();
    }

    public static class ChatRequest {
        @NotBlank
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestBody ChatRequest request) {
        // Forward the message to the Python chatbot API
        String url = chatbotBaseUrl + "/api/chat";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> payload = Map.of(
                "message", request.getMessage(),
                "user", userDetails != null ? userDetails.getUsername() : "anonymous"
        );

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> chatbotResponse = restTemplate.postForEntity(url, entity, Map.class);
            Object reply = chatbotResponse.getBody() != null ? chatbotResponse.getBody().get("reply") : null;
            return ResponseEntity.ok(Map.of("reply", reply != null ? reply.toString() : "No reply"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "Failed to contact chatbot service"));
        }
    }
}


