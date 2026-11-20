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

// OpenAI 호환 chat completions 를 쓰는 곳 공용: 이 PC 의 Ollama, OpenRouter, NVIDIA, Mistral
// baseUrl 은 .../v1 까지. 글만 있으면 textModel, 사진이 있으면 visionModel. PDF 는 못 읽는다
// local 이면 키 없이 보내고, 생각 과정을 끄라고 알린다 (작은 모델이 생각에 시간을 다 쓰지 않게)
public class OpenAiCompatLlmClient implements LlmClient {

    private final String apiKey;
    private final String textModel, visionModel;
    private final URI url;
    private final Duration timeout;
    private final boolean local;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenAiCompatLlmClient(String apiKey, String textModel, String visionModel, String baseUrl, int timeoutSeconds, boolean local,
            ObjectMapper mapper) {
        this.apiKey = apiKey.trim();
        this.textModel = textModel;
        this.visionModel = visionModel;
        this.url = URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.local = local;
        this.mapper = mapper;
        // 암호화 없는 로컬 주소에 HTTP/2 올리기를 시도하지 않게
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String ask(String system, String prompt, Attachment file) {
        if (!local && apiKey.isEmpty()) {
            throw new LlmException("LLM 키가 설정되지 않았어요.");
        }
        if (file != null && !file.mediaType().startsWith("image/")) {
            throw new LlmException("이 LLM 은 PDF 를 읽지 못해요.");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", file == null ? textModel : visionModel);
        if (local) {
            body.put("reasoning_effort", "none");
        }
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

        HttpRequest.Builder request = HttpRequest.newBuilder(url).timeout(timeout)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (!apiKey.isEmpty()) {
            request.header("authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("LLM 응답 오류 " + response.statusCode());
            }
            String text = mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asString("");
            // 생각 과정을 답 안에 섞어 주는 모델이 있다
            return text.replaceAll("(?s)<think>.*?</think>", "").strip();
        } catch (IOException | JacksonException e) {
            throw new LlmException("LLM 호출 실패: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM 호출 중단");
        }
    }
}
