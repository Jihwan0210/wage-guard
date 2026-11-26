package com.yellowdolphin.wageback.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.yellowdolphin.wageback.common.ApiException;

import tools.jackson.databind.json.JsonMapper;

// 가짜 카카오 서버로 요청 모양과 응답 읽기를 시험한다
class PlaceControllerTest {

    private static final String OK = """
            {"documents":[
             {"place_name":"노랑치킨 강남점","road_address_name":"서울 강남구 테헤란로 1","address_name":"서울 강남구 역삼동 1","phone":"02-123-4567","category_name":"음식점 > 치킨"},
             {"place_name":"노랑마트","road_address_name":"","address_name":"경기 수원시 팔달구 매산로1가 2","phone":"","category_name":"가정,생활 > 슈퍼마켓"}]}""";

    private final List<String> seen = new CopyOnWriteArrayList<>();
    private HttpServer server;

    private PlaceController controller(String key, int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", ex -> {
            seen.add(ex.getRequestHeaders().getFirst("Authorization") + " " + ex.getRequestURI().getRawQuery());
            byte[] out = (status == 200 ? OK : "{}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return new PlaceController(key, "http://127.0.0.1:" + server.getAddress().getPort() + "/search", JsonMapper.builder().build());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 가게_이름으로_찾으면_도로명_주소를_먼저_돌려준다() throws IOException {
        var places = controller("k1", 200).search(" 노랑치킨 ");
        assertThat(seen.get(0)).isEqualTo("KakaoAK k1 size=10&query=%EB%85%B8%EB%9E%91%EC%B9%98%ED%82%A8");
        assertThat(places).containsExactly(
                new PlaceController.Place("노랑치킨 강남점", "서울 강남구 테헤란로 1", "02-123-4567", "치킨"),
                new PlaceController.Place("노랑마트", "경기 수원시 팔달구 매산로1가 2", "", "슈퍼마켓"));
    }

    @Test
    void 키가_없거나_카카오가_실패하면_503() throws IOException {
        assertThatThrownBy(() -> controller("", 200).search("노랑치킨")).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("PLACE_UNAVAILABLE");
        stop();
        assertThatThrownBy(() -> controller("k1", 401).search("노랑치킨")).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus().value()).isEqualTo(503);
    }

    @Test
    void 너무_짧은_검색어는_400() throws IOException {
        assertThatThrownBy(() -> controller("k1", 200).search(" 가 ")).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus().value()).isEqualTo(400);
        assertThat(seen).isEmpty();
    }
}
