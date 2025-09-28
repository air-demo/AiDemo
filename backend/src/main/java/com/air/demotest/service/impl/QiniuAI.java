package com.air.demotest.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

@Service
public class QiniuAI {

    @Value("${openai.base-url}")
    private String baseUrl;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 非流式：一次性返回完整内容 */
    public String chatOnce(String userText, List<Message> history) throws Exception {
        // 组装 messages（把历史与当前 user 一起发送）
        List<Map<String, String>> msgs = new ArrayList<Map<String, String>>();
        if (history != null) {
            for (Message m : history) {
                Map<String, String> one = new LinkedHashMap<String, String>();
                one.put("role", m.getRole());
                one.put("content", m.getContent());
                msgs.add(one);
            }
        }
        Map<String, String> user = new LinkedHashMap<String, String>();
        user.put("role", "user");
        user.put("content", userText);
        msgs.add(user);

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("messages", msgs);
        body.put("stream", Boolean.FALSE);
        body.put("max_tokens", 4096);

        String json = mapper.writeValueAsString(body);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> entity = new HttpEntity<String>(json, headers);
        String url = baseUrl + "/chat/completions";

        ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST, entity, String.class);
        String raw = resp.getBody();

        // 解析 content：{"choices":[{"message":{"content":"..."} }]}
        Map parsed = mapper.readValue(raw, Map.class);
        List choices = (List) parsed.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map first = (Map) choices.get(0);
            Map message = (Map) first.get("message");
            if (message != null) {
                Object content = message.get("content");
                if (content != null) return content.toString();
            }
        }
        return raw; // 兜底：直接回原始 JSON
    }

    public void stream(String userMsg, SseEmitter emitter) {
        try {
            // 构建请求体
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("stream", Boolean.TRUE);
            body.put("messages", Arrays.asList(
                    new LinkedHashMap<String,String>() {{
                        put("role", "user");
                        put("content", userMsg);
                    }}
            ));
            String json = mapper.writeValueAsString(body);

            // HttpURLConnection
            URL url = new URL(baseUrl + "/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("utf-8"));
            }

            // 逐行读取 & 推送
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"));
            String line;
            StringBuilder buf = new StringBuilder();
            while ((line = br.readLine()) != null) {
                // 七牛/OpenAI 流格式：data: {\"choices\":[{\"delta\":{\"content\":\"xxx\"}}]}
                if (!line.startsWith("data:")) continue;
                if (line.equals("data: [DONE]")) break;
                String payload = line.substring(5).trim();
                Map obj = mapper.readValue(payload, Map.class);
                Map delta = (Map) ((Map)((List)obj.get("choices")).get(0)).get("delta");
                String piece = (String) delta.get("content");
                if (piece != null) {
                    buf.append(piece);
                    emitter.send(piece);           // 马上推给前端
                }
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }


    /** 简单消息对象（可放到独立文件） */
    public static class Message {
        private String role;     // "user" | "assistant" | "system"
        private String content;
        public Message() {}
        public Message(String role, String content){ this.role=role; this.content=content; }
        public String getRole(){ return role; }
        public void setRole(String role){ this.role = role; }
        public String getContent(){ return content; }
        public void setContent(String content){ this.content = content; }
    }
}