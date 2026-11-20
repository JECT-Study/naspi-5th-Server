package kr.ject.naspi.contents.application;

/**
 * 공시 목록용 이미지 미리보기.
 *
 * @param count 첨부 이미지 수
 * @param first 첫 번째 이미지의 서명 URL
 */
public record ImagePreview(int count, ImageUrl first) {
}
