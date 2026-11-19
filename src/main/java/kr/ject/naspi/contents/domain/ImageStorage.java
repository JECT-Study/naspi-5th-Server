package kr.ject.naspi.contents.domain;

import java.time.Duration;

/**
 * 이미지 파일 저장소. 운영은 GCS({@code GcsImageStorage}), 로컬·테스트는 파일 시스템({@code LocalImageStorage})을 쓴다.
 *
 * <p>저장소 오류는 {@link kr.ject.naspi.global.exception.NaspiRuntimeException}으로 던진다.
 */
public interface ImageStorage {

    /**
     * 파일을 저장한다. 같은 키의 객체가 이미 있으면 덮어쓰지 않고 실패한다.
     *
     * @param objectKey 객체 키
     * @param content 파일 내용
     * @param contentType Content-Type
     */
    void upload(String objectKey, byte[] content, String contentType);

    /**
     * 파일을 삭제한다. 객체가 이미 없으면 아무것도 하지 않는다.
     *
     * @param objectKey 객체 키
     */
    void delete(String objectKey);

    /**
     * 만료 시간 동안 인증 없이 파일을 내려받을 수 있는 URL을 발급한다. 권한 검증은 호출하는 쪽이 먼저 한다.
     * 반환한 URL은 접근 권한 자체이므로 로그에 남기지 않는다.
     *
     * @param objectKey 객체 키
     * @param expiration 유효 시간
     * @return 다운로드 URL
     */
    String signedUrl(String objectKey, Duration expiration);
}
