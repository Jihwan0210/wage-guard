package com.yellowdolphin.wageback.common.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// Google Gemini API (generateContent)
// 키를 쉼표로 여러 개 넣으면 돌아가며 쓰고, 한 키가 한도(429)에 걸리면 다음 키로 다시 보낸다
public class GeminiLlmClient implements LlmClient {

    static final String URL = "https://generativelanguage.googleapis.com";

    private final List<String> keys;
    private final URI url;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final AtomicInteger next = new AtomicInteger();

    public GeminiLlmClient(String apiKeys, String model, String baseUrl, int timeoutSeconds, ObjectMapper mapper) {
        this.keys = Arrays.stream(apiKeys.split(",")).map(String::trim).filter(k -> !k.isEmpty()).toList();
        this.url = URI.create(baseUrl.replaceAll("/+$", "") + "/v1beta/models/" + model + ":generateContent");
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String ask(String system, String prompt, Attachment file) {
        if (keys.isEmpty()) {
            throw new LlmException("LLM 키가 설정되지 않았어요.");
        }
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        ArrayNode parts = body.putArray("contents").addObject().put("role", "user").putArray("parts");
        if (file != null) {
            parts.addObject().putObject("inlineData").put("mimeType", file.mediaType())
                    .put("data", Base64.getEncoder().encodeToString(file.data()));
        }
        parts.addObject().put("text", prompt);
        String json = mapper.writeValueAsString(body);

        int first = Math.floorMod(next.getAndIncrement(), keys.size());
        int status = 0;
        for (int i = 0; i < keys.size(); i++) {
            HttpRequest request = HttpRequest.newBuilder(url).timeout(timeout)
                    .header("x-goog-api-key", keys.get((first + i) % keys.size()))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = send(request);
            status = response.statusCode();
            if (status == 200) {
                return text(response.body());
            }
            if (status != 429) {
                break;
            }
        }
        throw new LlmException("LLM 응답 오류 " + status);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LlmException("LLM 호출 실패: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM 호출 중단");
        }
    }

    // 첫 번째 답의 글자 조각을 이어 붙인다 (생각 과정 조각은 뺀다)
    private String text(String body) {
        try {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : mapper.readTree(body).path("candidates").path(0).path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) {
                    text.append(part.path("text").asString());
                }
            }
            return text.toString();
        } catch (JacksonException e) {
            throw new LlmException("LLM 호출 실패: " + e.getClass().getSimpleName());
        }
    }
}
