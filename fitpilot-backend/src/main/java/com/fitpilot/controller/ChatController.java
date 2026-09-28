package com.fitpilot.controller;

import com.fitpilot.dto.ChatRequest;
import com.fitpilot.model.ChatMessage;
import com.fitpilot.repo.ChatMessageRepository;
import com.fitpilot.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 实时教练：SSE 流式聊天 + 历史记录 */
@RestController
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageRepository chatRepo;

    public ChatController(ChatService chatService, ChatMessageRepository chatRepo) {
        this.chatService = chatService;
        this.chatRepo = chatRepo;
    }

    /** 发送消息，返回 SSE 流：token 增量、plan_updated、done、error 事件 */
    @PostMapping(path = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        chatService.stream(request.sessionId(), request.message(), emitter);
        return emitter;
    }

    @GetMapping("/api/chat/history")
    public List<ChatMessage> history(@RequestParam String sessionId) {
        return chatRepo.findTop40BySessionIdOrderByCreatedAtAsc(sessionId);
    }
}
