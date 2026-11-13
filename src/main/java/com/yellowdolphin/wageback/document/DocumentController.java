package com.yellowdolphin.wageback.document;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.yellowdolphin.wageback.common.Samples;

@RestController
@RequestMapping(value = "/api/v1/documents", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentController {

    private static final Set<String> ALLOWED = Set.of(".pdf", ".jpg", ".jpeg", ".png");

    // 임시: OCR 연결 전까지 파일 형식만 검사하고 예시 추출 결과를 돌려준다
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String extract(@RequestParam("file") MultipartFile file, @RequestParam("docType") String docType) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".hwp")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "한글(HWP) 파일은 PDF로 바꿔서 올려 주세요.");
        }
        if (ALLOWED.stream().noneMatch(name::endsWith)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDF, JPG, PNG 파일만 올릴 수 있어요.");
        }
        return Samples.read("document-extract").replace("{{docType}}", "CONTRACT".equals(docType) ? "CONTRACT" : "PAYSLIP");
    }
}
