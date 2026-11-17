package kr.ject.naspi.contents.application;

/**
 * 업로드할 이미지 파일. 웹 계층이 multipart 파일을 이 형태로 바꿔 넘긴다.
 *
 * @param originalFilename 원본 파일명
 * @param contentType 요청에 실린 Content-Type
 * @param content 파일 내용
 */
public record ImageFile(String originalFilename, String contentType, byte[] content) {
}
