package com.yellowdolphin.wageback.common.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// Groq (OpenAI 호환 chat completions). 글만 있으면 textModel, 사진이 있으면 visionModel. PDF 는 못 읽는다
public class GroqLlmClient implements LlmClient {

    static final String URL = "https://api.groq.com";

    private final String apiKey;
    private final String textModel, visionModel;
    private final URI url;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public GroqLlmClient(String apiKey, String textModel, String visionModel, String baseUrl, int timeoutSeconds, ObjectMapper mapper) {
        this.apiKey = apiKey.trim();
        this.textModel = textModel;
        this.visionModel = visionModel;
        this.url = URI.create(baseUrl.replaceAll("/+$", "") + "/openai/v1/chat/completions");
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String ask(String system, String prompt, Attachment file) {
        if (file != null && !file.mediaType().startsWith("image/")) {
            throw new LlmException("Groq 는 PDF 를 읽지 못해요.");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", file == null ? textModel : visionModel);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        ObjectNode user = messages.addObject().put("role", "user");
        if (file == null) {
            user.put("content", prompt);
        } else {
            ArrayNode content = user.putArray("content");
            content.addObject().put("type", "text").put("text", prompt);
            content.addObject().put("type", "image_url").putObject("image_url")
                    .put("url", "data:" + file.mediaType() + ";base64," + Base64.getEncoder().encodeToString(file.data()));
        }

        HttpRequest request = HttpRequest.newBuilder(url).timeout(timeout)
                .header("authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("LLM 응답 오류 " + response.statusCode());
            }
            return mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asString();
        } catch (IOException | JacksonException e) {
            throw new LlmException("LLM 호출 실패: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM 호출 중단");
        }
    }
}
