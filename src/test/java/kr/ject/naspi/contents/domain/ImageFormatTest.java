package kr.ject.naspi.contents.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageFormatTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1};
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};

    @Test
    @DisplayName("공시 이미지는 JPEG(.jpg/.jpeg), PNG 형식만 허용한다")
    void allowJpegAndPng() {
        assertThat(ImageFormat.detect("a.jpg", "image/jpeg", JPEG)).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect("a.JPEG", "image/jpeg", JPEG)).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect("a.png", "image/png", PNG)).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect("a.gif", "image/gif", "GIF89a".getBytes())).isEmpty();
    }

    @Test
    @DisplayName("확장자, Content-Type, 파일 내용 시그니처 중 하나라도 맞지 않으면 거절한다")
    void rejectMismatch() {
        assertThat(ImageFormat.detect("a.png", "image/png", JPEG)).isEmpty();
        assertThat(ImageFormat.detect("a.jpg", "image/png", JPEG)).isEmpty();
        assertThat(ImageFormat.detect("a.exe.jpg", "image/jpeg", "MZ....".getBytes())).isEmpty();
        assertThat(ImageFormat.detect("noext", "image/jpeg", JPEG)).isEmpty();
        assertThat(ImageFormat.detect(null, "image/jpeg", JPEG)).isEmpty();
        assertThat(ImageFormat.detect("a.jpg", "image/jpeg", new byte[] {(byte) 0xFF})).isEmpty();
    }

    @Test
    @DisplayName("객체 키는 원본 파일명을 쓰지 않고 리그별 UUID 경로로 만든다")
    void objectKey() {
        String key = Image.newObjectKey(3L, ImageFormat.PNG);
        Image image = Image.attach(3L, 5L, 0, key, "../../etc/passwd.png", ImageFormat.PNG, 9,
                LocalDateTime.of(2026, 11, 17, 9, 0));

        assertThat(key)
                .matches("images/leagues/3/[0-9a-f-]{36}\\.png")
                .isNotEqualTo(Image.newObjectKey(3L, ImageFormat.PNG));
        assertThat(image.getObjectKey()).isEqualTo(key);
        assertThat(image.getContentType()).isEqualTo("image/png");
    }
}
