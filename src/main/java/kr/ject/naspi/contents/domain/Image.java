package kr.ject.naspi.contents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공시에 첨부한 이미지 (ERD: disclosure_image). 파일 자체는 저장소({@link ImageStorage})에 있고
 * {@link #objectKey}로 찾는다. 서명 URL은 만료되므로 저장하지 않고 조회할 때마다 발급한다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "공시"): 이미지는 공시에 JPEG·PNG로 최대 4개 첨부하며,
 * 공시와 함께 등록·조회·삭제된다. 조회·삭제 권한은 공시 기준으로 판단한다.
 */
@Entity
@Table(name = "disclosure_image",
        uniqueConstraints = @UniqueConstraint(name = "uk_disclosure_image_sort_order",
                columnNames = {"disclosure_id", "sort_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image {

    /** 정책상 이미지 1장의 최대 크기 (50MB). */
    public static final long MAX_SIZE_BYTES = 50L * 1024 * 1024;

    /** 정책상 공시 1건에 첨부할 수 있는 최대 이미지 수. */
    public static final int MAX_COUNT_PER_DISCLOSURE = 4;

    /** 원본 파일명 컬럼 길이. 더 긴 이름은 잘라서 저장한다 (표시용이므로 정확할 필요 없음). */
    private static final int ORIGINAL_FILENAME_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    @Column(name = "disclosure_id", nullable = false, updatable = false)
    private Long disclosureId;

    /** 공시 안에서의 첨부 순서 (0부터). */
    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(name = "object_key", nullable = false, unique = true, updatable = false)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = ORIGINAL_FILENAME_LENGTH,
            updatable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Image(long leagueId, long disclosureId, int sortOrder, String objectKey,
                  String originalFilename, String contentType, long fileSize,
                  LocalDateTime createdAt) {
        this.leagueId = leagueId;
        this.disclosureId = disclosureId;
        this.sortOrder = sortOrder;
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.originalFilename =
                truncate(Objects.requireNonNull(originalFilename, "originalFilename"));
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.fileSize = fileSize;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * 새 파일의 객체 키를 만든다. 사용자 파일명을 쓰지 않고 UUID로 만들어 중복과 경로 조작을 막는다
     * ({@code images/leagues/{leagueId}/{uuid}.{ext}}). 공시를 저장하기 전에 파일을 먼저 올리므로
     * 공시 ID는 키에 넣지 않는다.
     *
     * @param leagueId 리그 ID
     * @param format 검증된 이미지 형식
     * @return 객체 키
     */
    public static String newObjectKey(long leagueId, ImageFormat format) {
        return "images/leagues/%d/%s.%s".formatted(leagueId, UUID.randomUUID(),
                format.extension());
    }

    /**
     * 저장소에 올린 파일을 공시에 첨부한다.
     *
     * @param leagueId 리그 ID
     * @param disclosureId 공시 ID
     * @param sortOrder 첨부 순서 (0부터)
     * @param objectKey {@link #newObjectKey}로 만든 객체 키
     * @param originalFilename 원본 파일명 (표시용)
     * @param format 검증된 이미지 형식
     * @param fileSize 파일 크기 (바이트)
     * @param createdAt 업로드 일시
     * @return 저장 전 이미지
     */
    public static Image attach(long leagueId, long disclosureId, int sortOrder, String objectKey,
                               String originalFilename, ImageFormat format, long fileSize,
                               LocalDateTime createdAt) {
        return new Image(leagueId, disclosureId, sortOrder, objectKey, originalFilename,
                format.contentType(), fileSize, createdAt);
    }

    private static String truncate(String filename) {
        return filename.length() <= ORIGINAL_FILENAME_LENGTH
                ? filename : filename.substring(0, ORIGINAL_FILENAME_LENGTH);
    }
}
