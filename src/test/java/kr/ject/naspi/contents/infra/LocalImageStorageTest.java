package kr.ject.naspi.contents.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalImageStorageTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("로컬 저장소는 키 경로에 파일을 저장·삭제하고, 이미 없는 파일 삭제는 무시한다")
    void uploadAndDelete() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(root, "http://localhost/local-images");

        storage.upload("images/league/1/a.png", new byte[] {1}, "image/png");

        assertThat(Files.readAllBytes(root.resolve("images/league/1/a.png"))).containsExactly(1);
        assertThat(storage.signedUrl("images/league/1/a.png", Duration.ofMinutes(5)))
                .isEqualTo("http://localhost/local-images/images/league/1/a.png");
        assertThatThrownBy(
                () -> storage.upload("images/league/1/a.png", new byte[] {2}, "image/png"))
                .isInstanceOf(NaspiRuntimeException.class);

        storage.delete("images/league/1/a.png");
        storage.delete("images/league/1/a.png");
        assertThat(root.resolve("images/league/1/a.png")).doesNotExist();
    }

    @Test
    @DisplayName("저장 경로 밖을 가리키는 키는 거절한다")
    void rejectTraversal() {
        LocalImageStorage storage = new LocalImageStorage(root, "http://localhost/local-images");

        assertThatThrownBy(() -> storage.upload("../evil.png", new byte[] {1}, "image/png"))
                .isInstanceOf(NaspiRuntimeException.class);
    }
}
