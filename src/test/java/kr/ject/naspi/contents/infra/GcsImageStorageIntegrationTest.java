package kr.ject.naspi.contents.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 GCS 버킷으로 업로드 → 서명 URL 다운로드 → 삭제를 확인한다. CI에는 GCP 인증 정보가 없으므로
 * {@code GCS_IT_BUCKET}이 있을 때만 실행한다.
 *
 * <pre>
 * gcloud auth application-default login \
 *     --impersonate-service-account=naspi-gcs-sa@naspi-511311.iam.gserviceaccount.com
 * GCS_IT_BUCKET=naspi-image-storage GCS_IT_PROJECT_ID=naspi-511311 \
 *     ./gradlew test --tests "*GcsImageStorageIntegrationTest"
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "GCS_IT_BUCKET", matches = ".+")
class GcsImageStorageIntegrationTest {

    @Test
    @DisplayName("GCS에 올린 이미지를 서명 URL로 내려받고, 삭제하면 같은 URL로 받을 수 없다")
    void roundTrip() throws Exception {
        GcsImageStorage storage = new GcsImageStorage(System.getenv("GCS_IT_PROJECT_ID"),
                System.getenv("GCS_IT_BUCKET"));
        String key = "images/it/" + UUID.randomUUID() + ".png";
        byte[] content = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        HttpClient http = HttpClient.newHttpClient();

        storage.upload(key, content, "image/png");
        try {
            URI url = URI.create(storage.signedUrl(key, Duration.ofMinutes(1)));
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(url).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).containsExactly(content);
            assertThat(response.headers().firstValue("Content-Type")).contains("image/png");

            storage.delete(key);
            assertThat(http.send(HttpRequest.newBuilder(url).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        } finally {
            storage.delete(key);
        }
    }
}
