package kr.ject.naspi.contents.api.disclosure;

import java.time.LocalDateTime;
import kr.ject.naspi.contents.application.ImageUrl;

/**
 * 공시 이미지. 서명 URL은 만료 전까지만 열 수 있다.
 *
 * @param imageId 이미지 ID
 * @param signedUrl 서명 URL
 * @param expiresAt 서명 URL 만료 일시 (KST). 이미지 저장 기간이 아니라 이 URL로 접근할 수 있는 마지막 시각이다.
 *                  지나면 URL이 403을 돌려주므로 공시를 다시 조회해 새 URL을 받는다 (이미지 파일은 그대로 남아 있다)
 */
record DisclosureImageResponse(long imageId, String signedUrl, LocalDateTime expiresAt) {

    static DisclosureImageResponse from(ImageUrl url) {
        return url == null ? null
                : new DisclosureImageResponse(url.imageId(), url.signedUrl(), url.expiresAt());
    }
}
