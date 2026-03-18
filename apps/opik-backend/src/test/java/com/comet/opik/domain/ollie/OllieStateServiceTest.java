package com.comet.opik.domain.ollie;

import com.comet.opik.infrastructure.OllieStateConfig;
import com.comet.opik.infrastructure.S3Config;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllieStateServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final String USER_NAME = "testuser@example.com";
    private static final byte[] GZIP_HEADER = {(byte) 0x1f, (byte) 0x8b};

    @Mock
    private S3Client s3Client;

    private OllieStateServiceImpl service;

    @BeforeEach
    void setUp() {
        S3Config s3Config = new S3Config();
        s3Config.setS3BucketName(BUCKET);

        OllieStateConfig ollieStateConfig = new OllieStateConfig();
        ollieStateConfig.setMaxUploadSizeBytes(1024);

        service = new OllieStateServiceImpl(s3Client, s3Config, ollieStateConfig);
    }

    private static byte[] gzipData(int size) {
        byte[] data = new byte[size];
        data[0] = GZIP_HEADER[0];
        data[1] = GZIP_HEADER[1];
        return data;
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Nested
    @DisplayName("upload")
    class Upload {

        @Test
        @DisplayName("uploads valid gzip data to correct S3 key")
        void uploadsToCorrectKey() throws Exception {
            byte[] data = gzipData(100);
            InputStream input = new ByteArrayInputStream(data);

            service.upload(USER_NAME, input);

            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

            PutObjectRequest request = captor.getValue();
            String expectedKey = "ollie-state/%s/ollie.db.gz".formatted(sha256Hex(USER_NAME));
            assertThat(request.bucket()).isEqualTo(BUCKET);
            assertThat(request.key()).isEqualTo(expectedKey);
            assertThat(request.contentType()).isEqualTo("application/gzip");
        }

        @Test
        @DisplayName("rejects non-gzip data")
        void rejectsNonGzip() {
            byte[] data = "not gzip".getBytes(StandardCharsets.UTF_8);
            InputStream input = new ByteArrayInputStream(data);

            assertThatThrownBy(() -> service.upload(USER_NAME, input))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not gzip-compressed");
        }

        @Test
        @DisplayName("rejects data exceeding size limit")
        void rejectsOversizedUpload() {
            byte[] data = gzipData(1025);
            InputStream input = new ByteArrayInputStream(data);

            assertThatThrownBy(() -> service.upload(USER_NAME, input))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("exceeds maximum size");
        }

        @Test
        @DisplayName("accepts data at exactly the size limit")
        void acceptsExactSizeLimit() throws Exception {
            byte[] data = gzipData(1024);
            InputStream input = new ByteArrayInputStream(data);

            service.upload(USER_NAME, input);

            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("rejects empty data")
        void rejectsEmptyData() {
            InputStream input = new ByteArrayInputStream(new byte[0]);

            assertThatThrownBy(() -> service.upload(USER_NAME, input))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not gzip-compressed");
        }

        @Test
        @DisplayName("rejects single byte data")
        void rejectsSingleByte() {
            InputStream input = new ByteArrayInputStream(new byte[]{(byte) 0x1f});

            assertThatThrownBy(() -> service.upload(USER_NAME, input))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not gzip-compressed");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        @DisplayName("rejects blank usernames")
        void rejectsBlankUserName(String userName) {
            byte[] data = gzipData(10);
            InputStream input = new ByteArrayInputStream(data);

            assertThatThrownBy(() -> service.upload(userName, input))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("userName must not be blank");
        }
    }

    @Nested
    @DisplayName("download")
    class Download {

        @Test
        @DisplayName("returns S3 stream for existing state")
        void returnsStream() throws Exception {
            @SuppressWarnings("unchecked")
            ResponseInputStream<GetObjectResponse> responseStream = mock(ResponseInputStream.class);
            String expectedKey = "ollie-state/%s/ollie.db.gz".formatted(sha256Hex(USER_NAME));

            when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

            InputStream result = service.download(USER_NAME);

            assertThat(result).isSameAs(responseStream);

            ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
            verify(s3Client).getObject(captor.capture());
            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().key()).isEqualTo(expectedKey);
        }

        @Test
        @DisplayName("throws NotFoundException when no state exists")
        void throwsNotFoundForMissing() {
            when(s3Client.getObject(any(GetObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("not found").build());

            assertThatThrownBy(() -> service.download(USER_NAME))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("No stored state found")
                    .hasCauseInstanceOf(NoSuchKeyException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " "})
        @DisplayName("rejects blank usernames")
        void rejectsBlankUserName(String userName) {
            assertThatThrownBy(() -> service.download(userName))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("userName must not be blank");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes correct S3 key")
        void deletesCorrectKey() throws Exception {
            service.delete(USER_NAME);

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());

            String expectedKey = "ollie-state/%s/ollie.db.gz".formatted(sha256Hex(USER_NAME));
            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().key()).isEqualTo(expectedKey);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " "})
        @DisplayName("rejects blank usernames")
        void rejectsBlankUserName(String userName) {
            assertThatThrownBy(() -> service.delete(userName))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("userName must not be blank");
        }
    }

    @Nested
    @DisplayName("S3 key construction")
    class KeyConstruction {

        @Test
        @DisplayName("different usernames produce different keys")
        void differentUsersGetDifferentKeys() throws Exception {
            byte[] data = gzipData(10);

            service.upload("user-a", new ByteArrayInputStream(data));
            service.upload("user-b", new ByteArrayInputStream(data));

            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client, times(2)).putObject(captor.capture(), any(RequestBody.class));

            assertThat(captor.getAllValues().get(0).key())
                    .isNotEqualTo(captor.getAllValues().get(1).key());
        }

        @Test
        @DisplayName("same username always produces same key")
        void sameUserGetsSameKey() throws Exception {
            byte[] data = gzipData(10);

            service.upload(USER_NAME, new ByteArrayInputStream(data));
            service.upload(USER_NAME, new ByteArrayInputStream(data));

            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client, times(2)).putObject(captor.capture(), any(RequestBody.class));

            assertThat(captor.getAllValues().get(0).key())
                    .isEqualTo(captor.getAllValues().get(1).key());
        }
    }
}
