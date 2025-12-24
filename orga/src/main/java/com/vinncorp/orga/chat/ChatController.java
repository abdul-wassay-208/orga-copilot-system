package com.vinncorp.orga.chat;

import com.vinncorp.orga.tenant.Tenant;
import com.vinncorp.orga.user.User;
import com.vinncorp.orga.user.UserRepository;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final RestTemplate restTemplate;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @Value("${chatbot.base-url:http://127.0.0.1:8000}")
    private String chatbotBaseUrl;

    public ChatController(RestTemplate restTemplate,
                         ConversationRepository conversationRepository,
                         MessageRepository messageRepository,
                         UserRepository userRepository) {
        this.restTemplate = restTemplate;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    public static class ChatRequest {
        @NotBlank
        private String message;
        private Long conversationId; // Optional: if provided, continue existing conversation

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestBody ChatRequest request) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Ensure user can only access their own tenant's data
        if (!user.getTenant().getIsActive()) {
            return ResponseEntity.status(403).body(Map.of("message", "Tenant is inactive"));
        }

        // Get or create conversation
        Conversation conversation;
        if (request.getConversationId() != null) {
            conversation = conversationRepository.findByIdAndUser(request.getConversationId(), user)
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));
            // Ensure messages are loaded by accessing them
            conversation.getMessages().size(); // Trigger lazy loading
            
            // Update title if it's still "New Chat" and this is the first user message
            if ("New Chat".equals(conversation.getTitle()) && conversation.getMessages().isEmpty()) {
                String title = request.getMessage().length() > 50 
                        ? request.getMessage().substring(0, 50) + "..." 
                        : request.getMessage();
                conversation.setTitle(title);
                conversationRepository.save(conversation); // Save the updated title
            }
        } else {
            // Create new conversation with first message as title
            String title = request.getMessage().length() > 50 
                    ? request.getMessage().substring(0, 50) + "..." 
                    : request.getMessage();
            conversation = new Conversation(user, title);
            conversationRepository.save(conversation);
        }

        // Build conversation history for context (before adding new message)
        List<Map<String, String>> conversationHistory = new java.util.ArrayList<>();
        List<Message> previousMessages = conversation.getMessages();
        
        // Include previous messages in conversation history
        for (Message msg : previousMessages) {
            Map<String, String> historyMsg = new java.util.HashMap<>();
            historyMsg.put("role", msg.getRole() == Message.MessageRole.USER ? "user" : "assistant");
            historyMsg.put("content", msg.getContent());
            conversationHistory.add(historyMsg);
        }

        // Save user message
        Message userMessage = new Message(conversation, request.getMessage(), Message.MessageRole.USER);
        messageRepository.save(userMessage);
        conversation.addMessage(userMessage);

        // Forward the message to the Python chatbot API
        String url = chatbotBaseUrl + "/chat";
        logger.info("Calling Python chatbot at: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Python API expects { "question": "...", "conversation_history": [...] }
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("question", request.getMessage());
        if (!conversationHistory.isEmpty()) {
            payload.put("conversation_history", conversationHistory);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            logger.info("Sending request to Python API: {}", payload);
            
            // Use exchange for more control and better error handling
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> chatbotResponse = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                Map.class
            );
            
            logger.info("Response status: {}", chatbotResponse.getStatusCode());
            logger.info("Received response from Python API: {}", chatbotResponse.getBody());
            
            String replyText;
            if (chatbotResponse.getBody() == null) {
                logger.warn("Response body is null!");
                replyText = "Chatbot returned empty response";
            } else {
                Object reply = chatbotResponse.getBody().get("reply");
                replyText = reply != null ? reply.toString() : chatbotResponse.getBody().toString();
            }

            // Save bot response
            Message botMessage = new Message(conversation, replyText, Message.MessageRole.BOT);
            messageRepository.save(botMessage);
            conversation.addMessage(botMessage);
            conversationRepository.save(conversation);

            return ResponseEntity.ok(Map.of(
                    "reply", replyText,
                    "conversationId", conversation.getId()
            ));
        } catch (RestClientException e) {
            logger.error("Error calling Python chatbot at {}: {}", url, e.getMessage(), e);
            String errorMsg = "Failed to contact chatbot service: " + e.getMessage();
            Message errorMessage = new Message(conversation, errorMsg, Message.MessageRole.BOT);
            messageRepository.save(errorMessage);
            conversation.addMessage(errorMessage);
            conversationRepository.save(conversation);
            
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", errorMsg, "conversationId", conversation.getId()));
        } catch (Exception e) {
            logger.error("Unexpected error calling Python chatbot: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal error: " + e.getMessage()));
        }
    }

    @PostMapping("/conversations")
    public ResponseEntity<?> createConversation(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Conversation conversation = new Conversation(user, "New Chat");
        conversation = conversationRepository.save(conversation);
        
        return ResponseEntity.ok(Map.of(
                "id", conversation.getId(),
                "title", conversation.getTitle(),
                "createdAt", conversation.getCreatedAt().toString()
        ));
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Tenant isolation: Only get conversations for this user (which automatically filters by tenant)
        List<Conversation> conversations = conversationRepository.findByUserOrderByUpdatedAtDesc(user);
        
        List<Map<String, Object>> result = conversations.stream()
                .map(c -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", c.getId());
                    map.put("title", c.getTitle());
                    map.put("createdAt", c.getCreatedAt().toString());
                    map.put("updatedAt", c.getUpdatedAt().toString());
                    map.put("messageCount", c.getMessages().size());
                    return map;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<?> getConversation(@AuthenticationPrincipal UserDetails userDetails,
                                            @PathVariable Long conversationId) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        
        // Ensure conversation belongs to user's tenant
        if (!conversation.getUser().getTenant().getId().equals(user.getTenant().getId())) {
            return ResponseEntity.status(403).body(Map.of("message", "Access denied"));
        }
        
        List<Map<String, Object>> messages = conversation.getMessages().stream()
                .map(m -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", m.getId());
                    map.put("content", m.getContent());
                    map.put("role", m.getRole().name());
                    map.put("createdAt", m.getCreatedAt().toString());
                    return map;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
                "id", conversation.getId(),
                "title", conversation.getTitle(),
                "createdAt", conversation.getCreatedAt().toString(),
                "updatedAt", conversation.getUpdatedAt().toString(),
                "messages", messages
        ));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<?> deleteConversation(@AuthenticationPrincipal UserDetails userDetails,
                                               @PathVariable Long conversationId) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        
        // Ensure conversation belongs to user's tenant
        if (!conversation.getUser().getTenant().getId().equals(user.getTenant().getId())) {
            return ResponseEntity.status(403).body(Map.of("message", "Access denied"));
        }
        
        conversationRepository.delete(conversation);
        
        return ResponseEntity.ok(Map.of("message", "Conversation deleted"));
    }
    
    private boolean checkUsageLimits(Tenant tenant) {
        // Check monthly message limit
        java.time.YearMonth currentMonth = java.time.YearMonth.now();
        java.time.LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();
        java.time.LocalDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59);
        
        long messageCount = messageRepository.findAll().stream()
                .filter(m -> {
                    com.vinncorp.orga.user.User msgUser = m.getConversation().getUser();
                    return msgUser.getTenant().getId().equals(tenant.getId()) &&
                           m.getCreatedAt().isAfter(startOfMonth) &&
                           m.getCreatedAt().isBefore(endOfMonth);
                })
                .count();
        
        return messageCount < tenant.getMaxMessagesPerMonth();
    }
}


