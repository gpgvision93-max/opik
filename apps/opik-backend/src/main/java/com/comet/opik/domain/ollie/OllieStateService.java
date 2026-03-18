package com.comet.opik.domain.ollie;

import com.comet.opik.infrastructure.OllieStateConfig;
import com.comet.opik.infrastructure.S3Config;
import com.google.inject.ImplementedBy;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@ImplementedBy(OllieStateServiceImpl.class)
public interface OllieStateService {
    void upload(String userName, InputStream inputStream) throws IOException;
    InputStream download(String userName);
    void delete(String userName);
}

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
class OllieStateServiceImpl implements OllieStateService {

    private static final String KEY_PREFIX = "ollie-state";
    private static final String FILE_NAME = "ollie.db.gz";
    private static final String CONTENT_TYPE = "application/gzip";

    private static final byte[] GZIP_MAGIC = {(byte) 0x1f, (byte) 0x8b};

    private final @NonNull S3Client s3Client;
    private final @NonNull @Config("s3Config") S3Config s3Config;
    private final @NonNull @Config("ollieStateConfig") OllieStateConfig ollieStateConfig;

    @Override
    @WithSpan
    public void upload(@NonNull String userName, @NonNull InputStream inputStream) throws IOException {
        validateUserName(userName);

        int maxSize = ollieStateConfig.getMaxUploadSizeBytes();
        byte[] data = inputStream.readNBytes(maxSize + 1);

        if (data.length > maxSize) {
            throw new BadRequestException("Upload exceeds maximum size of %d bytes".formatted(maxSize));
        }

        validateGzip(data);

        String key = buildKey(userName);

        log.debug("Uploading ollie state for user '{}', size {} bytes, key '{}'", userName, data.length, key);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Config.getS3BucketName())
                .key(key)
                .contentType(CONTENT_TYPE)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));
    }

    @Override
    @WithSpan
    public InputStream download(@NonNull String userName) {
        validateUserName(userName);
        String key = buildKey(userName);

        log.debug("Downloading ollie state for user '{}', key '{}'", userName, key);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(s3Config.getS3BucketName())
                .key(key)
                .build();

        try {
            return s3Client.getObject(request);
        } catch (NoSuchKeyException e) {
            log.warn("No ollie state found for user '{}', key '{}'", userName, key);
            throw new NotFoundException("No stored state found", e);
        }
    }

    @Override
    @WithSpan
    public void delete(@NonNull String userName) {
        validateUserName(userName);
        String key = buildKey(userName);

        log.debug("Deleting ollie state for user '{}', key '{}'", userName, key);

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(s3Config.getS3BucketName())
                .key(key)
                .build();

        s3Client.deleteObject(request);
    }

    private String buildKey(String userName) {
        String userHash = sha256Hex(userName);
        return "%s/%s/%s".formatted(KEY_PREFIX, userHash, FILE_NAME);
    }

    private void validateUserName(String userName) {
        if (userName.isBlank()) {
            throw new BadRequestException("userName must not be blank");
        }
    }

    private void validateGzip(byte[] data) {
        if (data.length < 2 || data[0] != GZIP_MAGIC[0] || data[1] != GZIP_MAGIC[1]) {
            throw new BadRequestException("Uploaded file is not gzip-compressed");
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
