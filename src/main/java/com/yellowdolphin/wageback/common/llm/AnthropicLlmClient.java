package com.yellowdolphin.wageback.common.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// Anthropic Messages API 를 Java HttpClient 로 부른다. 키 · 모델 · 시간 제한은 환경변수
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final URI URL = URI.create("https://api.anthropic.com/v1/messages");

    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public AnthropicLlmClient(@Value("${LLM_API_KEY:}") String apiKey, @Value("${LLM_MODEL:claude-haiku-5-5}") String model,
            @Value("${LLM_TIMEOUT_SECONDS:30}") int timeoutSeconds, ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String ask(String system, String prompt, Attachment file) {
        if (apiKey.isBlank()) {
            throw new LlmException("LLM 키가 설정되지 않았어요.");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 2000);
        body.put("system", system);
        ArrayNode content = body.putArray("messages").addObject().put("role", "user").putArray("content");
        if (file != null) {
            ObjectNode block = content.addObject().put("type", file.mediaType().equals("application/pdf") ? "document" : "image");
            block.putObject("source").put("type", "base64").put("media_type", file.mediaType())
                    .put("data", Base64.getEncoder().encodeToString(file.data()));
        }
        content.addObject().put("type", "text").put("text", prompt);

        HttpRequest request = HttpRequest.newBuilder(URL).timeout(timeout)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("LLM 응답 오류 " + response.statusCode());
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode block : mapper.readTree(response.body()).path("content")) {
                if ("text".equals(block.path("type").asString())) {
                    text.append(block.path("text").asString());
                }
            }
            return text.toString();
        } catch (IOException | JacksonException e) {
            throw new LlmException("LLM 호출 실패: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM 호출 중단");
        }
    }
}
