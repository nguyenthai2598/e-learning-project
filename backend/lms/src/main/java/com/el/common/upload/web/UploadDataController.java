package com.el.common.upload.web;

import com.el.common.upload.web.dto.UploadDTO;
import com.el.common.upload.application.dto.UploadResponse;
import com.el.common.upload.application.impl.awss3.AwsS3UploadService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadDataController {

    private final AwsS3UploadService awsS3UploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadSingle(@RequestParam(value = "private", defaultValue = "false") boolean isPrivate,
            @RequestPart @NotNull(message = "File to upload is required.") MultipartFile file) {
        log.info("Uploading single file: {}", file.getOriginalFilename());
        String url = awsS3UploadService.uploadFile(file, isPrivate);
        return ResponseEntity.ok(UploadResponse.success(url));
    }

    @DeleteMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteAll(@RequestBody UploadDTO uploadDTO) {
        log.info("Deleting files: {}", uploadDTO.urls());
        awsS3UploadService.deleteFiles(uploadDTO.urls());
        return ResponseEntity.noContent().build();
    }

}
