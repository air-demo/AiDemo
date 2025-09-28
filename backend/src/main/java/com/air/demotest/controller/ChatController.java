package com.air.demotest.controller;

import com.air.demotest.service.impl.QiniuAI;
import com.air.demotest.service.impl.QiniuAI.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
@CrossOrigin
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private QiniuAI qiniuAI;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public String chat(@RequestBody ChatReq req) throws Exception {
        List<QiniuAI.Message> history = req.getHistory(); // 可为 null
        String reply = qiniuAI.chatOnce(req.getMessage(), history);
        return reply; // 返回纯文本或 JSON 均可，这里返回文本最方便前端显示
    }


    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam("msg") String msg) throws IOException {
        SseEmitter emitter = new SseEmitter(0L);      // 不超时
        // 异步推流，避免阻塞 servlet 线程
        new Thread(() -> qiniuAI.stream(msg, emitter)).start();
        return emitter;
    }


    public static class ChatReq {
        private String message;
        private List<Message> history;
        public String getMessage(){ return message; }
        public void setMessage(String message){ this.message = message; }
        public List<Message> getHistory(){ return history; }
        public void setHistory(List<Message> history){ this.history = history; }
    }


}
