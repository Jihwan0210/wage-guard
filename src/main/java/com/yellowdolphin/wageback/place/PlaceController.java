package com.yellowdolphin.wageback.place;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yellowdolphin.wageback.common.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 가게 이름으로 주소 찾기: 카카오 로컬 키워드 검색을 서버가 대신 부른다
// 키(KAKAO_REST_API_KEY)는 환경변수나 llm-keys.properties 에만 두고 화면에는 보내지 않는다
@RestController
public class PlaceController {

    static final String URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    public record Place(String name, String address, String phone, String category) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String key;
    private final String url;
    private final ObjectMapper mapper;

    @Autowired
    public PlaceController(Environment env, ObjectMapper mapper) {
        this(env.getProperty("KAKAO_REST_API_KEY", "").strip(), URL, mapper);
    }

    PlaceController(String key, String url, ObjectMapper mapper) {
        this.key = key;
        this.url = url;
        this.mapper = mapper;
    }

    @GetMapping("/api/v1/places")
    public List<Place> search(@RequestParam(defaultValue = "") String q) {
        String query = q.strip();
        if (query.length() < 2 || query.length() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "q", "가게 이름을 2~50자로 적어 주세요.");
        }
        if (key.isEmpty()) {
            throw unavailable();
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url + "?size=10&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                .header("Authorization", "KakaoAK " + key).timeout(Duration.ofSeconds(5)).GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw unavailable();
            }
            List<Place> out = new ArrayList<>();
            for (JsonNode d : mapper.readTree(response.body()).path("documents")) {
                String road = d.path("road_address_name").asString("");
                String category = d.path("category_name").asString("");
                out.add(new Place(d.path("place_name").asString(""), road.isBlank() ? d.path("address_name").asString("") : road,
                        d.path("phone").asString(""), category.substring(category.lastIndexOf('>') + 1).strip()));
            }
            return out;
        } catch (IOException e) {
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PLACE_UNAVAILABLE", null, "지금은 가게를 찾을 수 없어요. 주소를 직접 적어 주세요.");
    }
}
