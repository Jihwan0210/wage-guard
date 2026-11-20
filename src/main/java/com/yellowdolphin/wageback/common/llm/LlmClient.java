package com.yellowdolphin.wageback.common.llm;

// LLM 호출. 시험에서는 가짜로 바꿔 끼운다
public interface LlmClient {

    // 답 글자를 돌려준다. 키 없음 · 실패 · 시간 초과는 LlmException
    String ask(String system, String prompt, Attachment file);

    // 같이 보낼 파일(이미지 · PDF). 없으면 null
    record Attachment(String mediaType, byte[] data) {
    }
}
