package com.yellowdolphin.wageback.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

// 실제 기능이 붙기 전까지 API.md 형식의 예시 응답을 돌려주기 위한 도우미
public final class Samples {

    private Samples() {
    }

    public static String read(String name) {
        try {
            return StreamUtils.copyToString(new ClassPathResource("samples/" + name + ".json").getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
