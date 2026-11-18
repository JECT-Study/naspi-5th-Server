package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;

/**
 * 이미지 다운로드용 서명 URL. {@link #signedUrl}은 접근 권한 자체이므로 로그에 남기지 않는다
 * (요청 로그는 JSON 키 {@code signedUrl} 값을 가린다).
 *
 * @param imageId 이미지 ID
 * @param signedUrl 서명 URL (만료 전까지 여러 번 사용 가능)
 * @param expiresAt 서명 URL 만료 일시 (KST). 이미지 저장 만료가 아니라 URL 접근 가능 기한이며, 지나면 새 URL을
 *                  발급받아야 한다 (이미지 파일은 공시가 삭제될 때까지 남아 있다)
 */
public record ImageUrl(long imageId, String signedUrl, LocalDateTime expiresAt) {
}
