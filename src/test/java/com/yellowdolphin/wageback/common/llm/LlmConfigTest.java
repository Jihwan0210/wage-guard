package com.yellowdolphin.wageback.common.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import tools.jackson.databind.json.JsonMapper;

// 설정에 따라 어느 LLM 을 어떤 순서로 쓰는지
class LlmConfigTest {

    static List<String> names(MockEnvironment env) {
        return LlmConfig.clients(env, JsonMapper.builder().build()).stream().map(c -> c.getClass().getSimpleName()).toList();
    }

    @Test
    void 키가_있는_것만_Claude_Gemini_Groq_순서로() {
        assertThat(names(new MockEnvironment())).containsExactly("AnthropicLlmClient"); // 아무것도 없으면 Claude (키 없음 → 503)
        assertThat(names(new MockEnvironment().withProperty("GROQ_API_KEY", "g").withProperty("GEMINI_API_KEY", "m")))
                .containsExactly("GeminiLlmClient", "GroqLlmClient");
        assertThat(names(new MockEnvironment().withProperty("GROQ_API_KEY", "g").withProperty("LLM_API_KEY", "c")))
                .containsExactly("AnthropicLlmClient", "GroqLlmClient");
        assertThat(names(new MockEnvironment().withProperty("GROQ_API_KEY", "g").withProperty("GEMINI_API_KEY", "m").withProperty("LLM_API_KEY", "c")))
                .containsExactly("AnthropicLlmClient", "GeminiLlmClient", "GroqLlmClient");
    }

    @Test
    void LLM_PROVIDER_한_줄이면_그것_하나만() {
        MockEnvironment all = new MockEnvironment().withProperty("GEMINI_API_KEY", "m").withProperty("GROQ_API_KEY", "g")
                .withProperty("LLM_API_KEY", "c");
        assertThat(names(all.withProperty("LLM_PROVIDER", "groq"))).containsExactly("GroqLlmClient");
        assertThat(names(all.withProperty("LLM_PROVIDER", " Gemini "))).containsExactly("GeminiLlmClient");
        assertThat(names(all.withProperty("LLM_PROVIDER", "claude"))).containsExactly("AnthropicLlmClient");
        assertThat(names(all.withProperty("LLM_PROVIDER", "ollama"))).containsExactly("OpenAiCompatLlmClient");
    }
}
