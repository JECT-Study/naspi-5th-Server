package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;

/**
 * 이미지 다운로드용 서명 URL. {@link #signedUrl}은 접근 권한 자체이므로 로그에 남기지 않는다
 * (요청 로그는 JSON 키 {@code signedUrl} 값을 가린다).
 *
 * @param imageId 이미지 ID
 * @param signedUrl 서명 URL (만료 전까지 여러 번 사용 가능)
 * @param expiresAt 만료 일시 (KST)
 */
public record ImageUrl(long imageId, String signedUrl, LocalDateTime expiresAt) {
}
