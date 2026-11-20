package kr.ject.naspi.contents.application;

import kr.ject.naspi.contents.domain.ImageFormat;

/**
 * 저장소에 올렸지만 아직 공시에 첨부(메타데이터 저장)하지 않은 이미지 파일.
 * {@link ImageService#attach}로 첨부하거나, 공시 저장이 실패하면 {@link ImageService#discard}로 지운다.
 *
 * @param objectKey 객체 키
 * @param originalFilename 원본 파일명
 * @param format 검증된 형식
 * @param fileSize 파일 크기 (바이트)
 */
public record StoredImage(String objectKey, String originalFilename, ImageFormat format,
                          long fileSize) {
}
