package com.composerai.api.controller;

import com.composerai.api.service.EmailParsingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class EmailFileParseController {

    private final EmailParsingService emailParsingService;

    @PostMapping(value = "/parse-email", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> parseEmail(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(emailParsingService.parseEmailFile(file));
    }
}
