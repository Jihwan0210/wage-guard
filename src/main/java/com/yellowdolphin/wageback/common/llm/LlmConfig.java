package com.yellowdolphin.wageback.common.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import tools.jackson.databind.ObjectMapper;

// 어느 LLM 을 쓸지 고른다. 키는 환경변수나 llm-keys.properties (git 에 안 올림)
// 기본: 키가 있는 것을 Claude → Gemini → Groq 순서로 쓰고, 앞이 실패하면 다음으로 넘긴다 (Claude 모델은 LLM_MODEL)
// LLM_PROVIDER=gemini|groq|claude|ollama 한 줄이면 그것 하나만 쓴다
@Configuration
public class LlmConfig {

    @Bean
    LlmClient llmClient(Environment env, ObjectMapper mapper) {
        List<LlmClient> chain = clients(env, mapper);
        LoggerFactory.getLogger(LlmConfig.class).info("LLM: {}",
                String.join(" → ", chain.stream().map(c -> c.getClass().getSimpleName().replace("LlmClient", "")).toList()));
        return chain.size() == 1 ? chain.get(0) : fallback(chain);
    }

    static List<LlmClient> clients(Environment env, ObjectMapper mapper) {
        int timeout = env.getProperty("LLM_TIMEOUT_SECONDS", Integer.class, 30);
        String only = env.getProperty("LLM_PROVIDER", "").strip().toLowerCase(Locale.ROOT);
        String gemini = env.getProperty("GEMINI_API_KEY", ""), groq = env.getProperty("GROQ_API_KEY", "");
        String claude = env.getProperty("LLM_API_KEY", "");

        List<LlmClient> chain = new ArrayList<>();
        if (only.isEmpty() ? !claude.isBlank() : only.equals("claude")) {
            chain.add(new AnthropicLlmClient(claude, env.getProperty("LLM_MODEL", "claude-haiku-5-5"), timeout, mapper));
        }
        if (only.isEmpty() ? !gemini.isBlank() : only.equals("gemini")) {
            chain.add(new GeminiLlmClient(gemini, env.getProperty("GEMINI_MODEL", "gemini-3.1-flash-lite"), GeminiLlmClient.URL, timeout, mapper));
        }
        if (only.isEmpty() ? !groq.isBlank() : only.equals("groq")) {
            chain.add(new GroqLlmClient(groq, env.getProperty("GROQ_MODEL", "openai/gpt-oss-120b"),
                    env.getProperty("GROQ_VISION_MODEL", "qwen/qwen3.8-27b"), GroqLlmClient.URL, timeout, mapper));
        }
        // LLM_PROVIDER=ollama: 이 PC 의 로컬 모델(키 없음). 느리니 2분(OLLAMA_TIMEOUT_SECONDS)을 넘기면 포기
        if (only.equals("ollama")) {
            chain.add(new OpenAiCompatLlmClient("", env.getProperty("OLLAMA_MODEL", "qwen3.5:4b"), env.getProperty("OLLAMA_VISION_MODEL", "qwen3-vl:4b"),
                    env.getProperty("OLLAMA_URL", "http://localhost:11434/v1"), env.getProperty("OLLAMA_TIMEOUT_SECONDS", Integer.class, 120), true, mapper));
        }
        if (chain.isEmpty()) {
            chain.add(new AnthropicLlmClient(claude, env.getProperty("LLM_MODEL", "claude-haiku-5-5"), timeout, mapper));
        }
        return chain;
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
