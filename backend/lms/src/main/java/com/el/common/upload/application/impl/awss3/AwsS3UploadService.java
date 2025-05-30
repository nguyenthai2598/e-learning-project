package com.el.common.upload.application.impl.awss3;

import com.el.common.upload.application.UploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AwsS3UploadService implements UploadService {

    @Value("${s3-access-key}")
    private String accessKey;

    @Value("${s3-secret-key}")
    private String secretKey;

    @Value("${s3-bucket-name}")
    private String bucketName;

    @Value("${s3-endpoint}")
    private String endpoint;

    private final S3Client s3Client;

    public AwsS3UploadService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public String uploadFile(MultipartFile file, boolean isPrivate) {
        String fileName = file.getOriginalFilename();

        try {
            return uploadFileToS3(fileName, file.getContentType(), file.getBytes(), isPrivate);
        } catch (IOException e) {
            log.error("Error reading file in uploadFile: {}", e.getMessage());
            throw new AmazonServiceS3Exception("Error reading file: " + e.getMessage());
        }
    }

    @Override
    public String uploadFile(byte[] fileContent, String fileName, String contentType, boolean isPrivate) {
        return uploadFileToS3(fileName, contentType, fileContent, isPrivate);
    }

    @Override
    public byte[] downloadFile(String url) {
        String key = cutURL(url);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        try (InputStream inputStream = s3Client.getObject(getObjectRequest)) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            log.error("Download media failed: {}", e.getMessage());
            throw new AmazonServiceS3Exception("Download media failed. " + e.getMessage());
        }
    }

    @Override
    public String generatePreSignedUrl(String url) {
        try {
            S3Presigner presigner = S3Presigner.builder()
                    .region(Region.AP_SOUTHEAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                            accessKey,
                            secretKey
                    )))
                    .build();

            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(cutURL(url))
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10))
                    .getObjectRequest(objectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            log.info("Presigned URL: [{}]", presignedRequest.url().toString());
            log.info("HTTP method: [{}]", presignedRequest.httpRequest().method());

            return presignedRequest.url().toExternalForm();
        } catch (Exception e) {
            log.error("Error generating pre-signed URL: {}", e.getMessage());
            throw new RuntimeException("Failed to generate pre-signed URL.", e);
        }
    }


    @Override
    public void deleteFiles(List<String> urls) {
        DeleteObjectsRequest deleteObjects = DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(builder -> builder
                        .objects(urls.stream()
                                .map(this::cutURL)
                                .map(key -> ObjectIdentifier.builder().key(key).build())
                                .toList())
                ).build();
        try {
            s3Client.deleteObjects(deleteObjects);
        } catch (Exception e) {
            log.error("Delete media failed: {}", e.getMessage());
            throw new AmazonServiceS3Exception("Delete media failed. " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> startMultipartUpload(String fileName) {
        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();
        CreateMultipartUploadResponse multipartUpload = s3Client.createMultipartUpload(request);
        return Map.of(
                "uploadId", multipartUpload.uploadId(),
                "key", fileName
        );
    }

    private String uploadFileToS3(String fileName, String contentType, byte[] fileContent, boolean isPrivate) {
        if (fileName == null || fileName.isEmpty()) {
            throw new AmazonServiceS3Exception("File name is null or empty.");
        }

        String key = UUID.randomUUID() + "_" + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .acl(isPrivate ? ObjectCannedACL.PRIVATE : ObjectCannedACL.PUBLIC_READ)
                .build();

        try {
            // Upload the byte array directly
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileContent));
            log.info("Upload media for {} successfully.", key);
        } catch (Exception e) {
            log.error("Upload media for {} failed: {}", key, e.getMessage());
            throw new AmazonServiceS3Exception("Upload media failed. " + e.getMessage());
        }

        return endpoint + "/" + key;
    }

    private String cutURL(String url) {
        String prefix = "https://bookstore-bucket.sgp1.digitaloceanspaces.com/";
        if (url.startsWith(prefix)) {
            return url.substring(prefix.length());
        } else {
            throw new IllegalArgumentException("Invalid URL prefix: " + url);
        }
    }

}
