package com.yellowdolphin.wageback.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.yellowdolphin.wageback.common.ApiException;

@RestController
@RequestMapping(value = "/api/v1/documents", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentController {

    static final long MAX_SIZE = 10L * 1024 * 1024;
    // 한 번에 올리는 최대 장 수 (급여명세서 여러 달 + 근로계약서)
    static final int MAX_PAGES = 6;

    private static final Map<String, String> TYPES = Map.of(
            ".pdf", "application/pdf", ".jpg", "image/jpeg", ".jpeg", "image/jpeg", ".png", "image/png");

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    // 파일은 메모리에서만 읽고 저장 · 로그하지 않는다
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentService.Result extract(@RequestParam("file") MultipartFile file, @RequestParam("docType") String docType)
            throws IOException {
        return service.extract(docType, check(file, docType, "file", "docType"), file.getBytes());
    }

    // 여러 장을 한 번에: files[i] 의 서류 종류는 docTypes[i]
    @PostMapping(value = "/extract-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentService.BatchResult extractBatch(@RequestParam("files") List<MultipartFile> files,
            @RequestParam("docTypes") List<String> docTypes) throws IOException {
        if (files.size() > MAX_PAGES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_FILES", "files", "한 번에 " + MAX_PAGES + "장까지 올릴 수 있어요.");
        }
        if (files.size() != docTypes.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DOC_TYPE", "docTypes", "사진마다 서류 종류를 하나씩 골라 주세요.");
        }
        List<DocumentService.Page> pages = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String mediaType = check(file, docTypes.get(i), "files[" + i + "]", "docTypes[" + i + "]");
            pages.add(new DocumentService.Page(docTypes.get(i), mediaType, file.getBytes()));
        }
        return service.extractBatch(pages);
    }

    // 형식 · 용량 · 서류 종류를 보고 미디어 타입을 돌려준다
    private static String check(MultipartFile file, String docType, String fileField, String typeField) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".hwp")) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE", fileField, "한글(HWP) 파일은 PDF로 바꿔서 올려 주세요.");
        }
        String mediaType = TYPES.entrySet().stream().filter(e -> name.endsWith(e.getKey())).map(Map.Entry::getValue).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE", fileField, "PDF, JPG, PNG 파일만 올릴 수 있어요."));
        if (file.getSize() > MAX_SIZE) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", fileField, "10MB 이하 파일만 올릴 수 있어요.");
        }
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", fileField, "빈 파일이에요.");
        }
        if (!"CONTRACT".equals(docType) && !"PAYSLIP".equals(docType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DOC_TYPE", typeField, "서류 종류는 CONTRACT 또는 PAYSLIP 이어야 해요.");
        }
        return mediaType;
    }
}
