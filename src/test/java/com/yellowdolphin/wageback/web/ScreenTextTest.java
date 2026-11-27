package com.yellowdolphin.wageback.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

// 화면 글 파일(i18n)과 화면 코드(app.js)가 서로 맞는지.
// 화면을 따라가는 흐름 시험(서류 있음, 서류 없음, 읽기 실패)은 브라우저가 필요해 src/test/e2e/upload_flow.py 로 따로 돌린다
class ScreenTextTest {

    static final Path STATIC = Path.of("src/main/resources/static");
    final JsonMapper mapper = new JsonMapper();

    @SuppressWarnings("unchecked")
    Map<String, Object> lang(String code) throws IOException {
        return mapper.readValue(Files.readString(STATIC.resolve("i18n/" + code + ".json")), Map.class);
    }

    @Test
    void 영어와_베트남어에_한국어_화면_글이_다_있다() throws IOException {
        Map<String, Object> ko = lang("ko");
        for (String code : List.of("en", "vi")) {
            Map<String, Object> other = lang(code);
            List<String> missing = new ArrayList<>();
            for (String k : ko.keySet()) {
                // 쉬운 말(머리줄 단추 top.easy 포함)은 한국어만, 법 조문 이름(.law)은 한국어 원문을 그대로 보여 준다
                if (k.startsWith("_") || k.startsWith("easy.") || k.equals("top.easy") || k.endsWith(".law")) continue;
                if (!other.containsKey(k)) missing.add(k);
            }
            assertThat(missing).as(code + " 에 없는 키").isEmpty();
        }
    }

    @Test
    void 화면_코드가_부르는_글_키가_한국어_파일에_있다() throws IOException {
        Map<String, Object> ko = lang("ko");
        String js = Files.readString(STATIC.resolve("js/app.js"));
        // t('키') 또는 t('키', {...}) 처럼 글자 그대로 쓴 키만 본다 (t('sit.' + x) 처럼 만든 키는 뺀다)
        Matcher m = Pattern.compile("\\bt\\('([a-zA-Z0-9_.]+)'\\s*[,)]").matcher(js);
        TreeSet<String> missing = new TreeSet<>();
        while (m.find()) {
            if (!ko.containsKey(m.group(1))) missing.add(m.group(1));
        }
        assertThat(missing).as("ko.json 에 없는 키").isEmpty();
    }

    @Test
    void 따로_있던_서류_올리기_화면은_없고_상황_화면이_읽기를_보여_준다() throws IOException {
        String js = Files.readString(STATIC.resolve("js/app.js"));
        assertThat(js).doesNotContain("upload: vUpload");
        assertThat(js).contains("sit: vSit").contains("class=\"read-ind\"").contains("id=\"read-err\"");
        assertThat(lang("ko")).containsKeys("sit.reading", "sit.done", "sit.doneSub", "sit.again", "sit.noDoc", "start.dropTitle", "start.manualBtn");
    }
}
