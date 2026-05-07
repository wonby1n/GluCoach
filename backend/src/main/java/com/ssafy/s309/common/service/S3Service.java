package com.ssafy.s309.common.service;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class S3Service {

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;

  @Value("${aws.s3.bucket}")
  private String bucket;

  public String upload(MultipartFile file, String folder) throws Exception {
    String key = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(file.getContentType())
            .build(),
        RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    return key;
  }

  public String getPresignedDownloadUrl(String key) {
    GetObjectPresignRequest request =
        GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(60))
            .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
            .build();
    return s3Presigner.presignGetObject(request).url().toString();
  }

  public String getPresignedUploadUrl(String key, String contentType) {
    PutObjectPresignRequest request =
        PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(10))
            .putObjectRequest(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build())
            .build();
    return s3Presigner.presignPutObject(request).url().toString();
  }

  public void delete(String key) {
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }

  public String uploadBytes(byte[] bytes, String key, String contentType) {
    s3Client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
        RequestBody.fromBytes(bytes));
    return key;
  }
}
