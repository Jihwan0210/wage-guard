package com.yellowdolphin.wageback.common.llm;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import tools.jackson.databind.ObjectMapper;

// 키가 있는 것으로 LLM 을 고른다. 키는 환경변수나 llm-keys.properties (git 에 안 올림)
// 무료 키(GEMINI_API_KEY, GROQ_API_KEY)가 있으면 Gemini → Groq 순서로 쓰고 앞이 실패하면 다음으로 넘긴다
// 둘 다 없으면 Claude (LLM_API_KEY)
@Configuration
public class LlmConfig {

    @Bean
    LlmClient llmClient(Environment env, ObjectMapper mapper) {
        int timeout = env.getProperty("LLM_TIMEOUT_SECONDS", Integer.class, 30);
        List<LlmClient> chain = new ArrayList<>();
        String gemini = env.getProperty("GEMINI_API_KEY", "");
        if (!gemini.isBlank()) {
            chain.add(new GeminiLlmClient(gemini, env.getProperty("GEMINI_MODEL", "gemini-flash-latest"), GeminiLlmClient.URL, timeout, mapper));
        }
        String groq = env.getProperty("GROQ_API_KEY", "");
        if (!groq.isBlank()) {
            chain.add(new GroqLlmClient(groq, env.getProperty("GROQ_MODEL", "openai/gpt-oss-120b"),
                    env.getProperty("GROQ_VISION_MODEL", "qwen/qwen3.8-27b"), GroqLlmClient.URL, timeout, mapper));
        }
        LoggerFactory.getLogger(LlmConfig.class).info("LLM: {}", chain.isEmpty() ? "Claude"
                : String.join(" → ", chain.stream().map(c -> c.getClass().getSimpleName().replace("LlmClient", "")).toList()));
        if (chain.isEmpty()) {
            return new AnthropicLlmClient(env.getProperty("LLM_API_KEY", ""), env.getProperty("LLM_MODEL", "claude-haiku-5-5"), timeout, mapper);
        }
        return fallback(chain);
    }

    // 앞에서부터 묻고, 실패(키 한도 · 오류 · 시간 초과)하면 다음 것에 묻는다. 다 실패하면 마지막 오류
    static LlmClient fallback(List<LlmClient> chain) {
        return (system, prompt, file) -> {
            LlmException last = null;
            for (LlmClient c : chain) {
                try {
                    return c.ask(system, prompt, file);
                } catch (LlmException e) {
                    last = e;
                }
            }
            throw last;
        };
    }
}
