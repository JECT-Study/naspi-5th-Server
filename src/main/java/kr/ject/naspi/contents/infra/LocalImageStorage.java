package kr.ject.naspi.contents.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import kr.ject.naspi.contents.domain.ImageStorage;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 파일 시스템 이미지 저장소 ({@code naspi.image.storage: local}). 로컬 개발·테스트에서 GCP 인증 없이 쓴다.
 *
 * <p>서명 URL 대신 정적 리소스 경로 URL을 돌려준다. 만료가 없으므로 운영에서 쓰면 안 된다.
 * local 프로필은 이 경로를 {@code spring.web.resources.static-locations}로 열어 둔다.
 */
@Component
@ConditionalOnProperty(name = "naspi.image.storage", havingValue = "local")
class LocalImageStorage implements ImageStorage {

    private final Path root;
    private final String baseUrl;

    LocalImageStorage(@Value("${naspi.image.local.dir}") Path root,
                      @Value("${naspi.image.local.base-url}") String baseUrl) {
        this.root = root.toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
    }

    @Override
    public void upload(String objectKey, byte[] content, String contentType) {
        Path file = resolve(objectKey);
        try {
            Files.createDirectories(file.getParent());
            // CREATE_NEW(기본 write의 CREATE와 달리) 같은 키가 있으면 실패한다.
            Files.write(file, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new NaspiRuntimeException("로컬 이미지 저장 실패: objectKey=" + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            throw new NaspiRuntimeException("로컬 이미지 삭제 실패: objectKey=" + objectKey, e);
        }
    }

    @Override
    public String signedUrl(String objectKey, Duration expiration) {
        return baseUrl + "/" + objectKey;
    }

    /** 키는 서버가 만들지만, 혹시라도 저장 경로 밖을 가리키지 않게 막는다. */
    private Path resolve(String objectKey) {
        Path file = root.resolve(objectKey).normalize();
        if (!file.startsWith(root)) {
            throw new NaspiRuntimeException("저장 경로를 벗어난 객체 키: " + objectKey);
        }
        return file;
    }
}
