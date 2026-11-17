package kr.ject.naspi.contents.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 업로드할 수 있는 이미지 형식.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "공시"): 이미지는 JPEG(.jpg/.jpeg), PNG 형식만 첨부할 수 있다.
 * 확장자와 Content-Type은 클라이언트가 바꿀 수 있으므로 파일 앞부분의 시그니처(매직 바이트)까지 일치해야 인정한다.
 */
public enum ImageFormat {

    JPEG("jpg", "image/jpeg", List.of("jpg", "jpeg"),
            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("png", "image/png", List.of("png"),
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

    private final String extension;
    private final String contentType;
    private final List<String> allowedExtensions;
    private final byte[] signature;

    ImageFormat(String extension, String contentType, List<String> allowedExtensions,
                byte[] signature) {
        this.extension = extension;
        this.contentType = contentType;
        this.allowedExtensions = allowedExtensions;
        this.signature = signature;
    }

    /**
     * 파일명 확장자, Content-Type, 파일 내용 시그니처가 모두 같은 형식을 가리킬 때만 그 형식을 돌려준다.
     *
     * @param filename 원본 파일명
     * @param contentType 요청에 실린 Content-Type
     * @param content 파일 내용
     * @return 형식, 허용하지 않는 형식이거나 셋이 서로 맞지 않으면 빈 값
     */
    public static Optional<ImageFormat> detect(String filename, String contentType,
                                               byte[] content) {
        if (filename == null || contentType == null || content == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        String ext = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        String type = contentType.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> format.allowedExtensions.contains(ext))
                .filter(format -> format.contentType.equals(type))
                .filter(format -> format.matchesSignature(content))
                .findFirst();
    }

    private boolean matchesSignature(byte[] content) {
        return content.length >= signature.length
                && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }

    /** 저장소 객체 키에 붙일 확장자. */
    public String extension() {
        return extension;
    }

    /** 저장소에 기록하고 응답에 돌려줄 Content-Type. */
    public String contentType() {
        return contentType;
    }
}
