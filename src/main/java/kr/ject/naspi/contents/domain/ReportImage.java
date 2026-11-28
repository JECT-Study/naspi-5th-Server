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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제보에 첨부한 이미지 (ERD: report_image). 파일 자체는 저장소({@link ImageStorage})에 있고
 * {@link #objectKey}로 찾는다. 서명 URL은 만료되므로 저장하지 않고 필요할 때마다 발급한다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "뉴스 - 제보 기반 AI 뉴스"): 이미지는 제보에 JPEG·PNG로 최대
 * 4개 첨부하며, 개수·크기 규칙은 공시 이미지({@link Image})와 같다.
 */
@Entity
@Table(name = "report_image",
        uniqueConstraints = @UniqueConstraint(name = "uk_report_image_sort_order",
                columnNames = {"report_id", "sort_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportImage {

    /** 원본 파일명 컬럼 길이. 더 긴 이름은 잘라서 저장한다 (표시용이므로 정확할 필요 없음). */
    private static final int ORIGINAL_FILENAME_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    @Column(name = "report_id", nullable = false, updatable = false)
    private Long reportId;

    /** 제보 안에서의 첨부 순서 (0부터). */
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

    private ReportImage(long leagueId, long reportId, int sortOrder, String objectKey,
                        String originalFilename, String contentType, long fileSize,
                        LocalDateTime createdAt) {
        this.leagueId = leagueId;
        this.reportId = reportId;
        this.sortOrder = sortOrder;
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.originalFilename =
                truncate(Objects.requireNonNull(originalFilename, "originalFilename"));
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.fileSize = fileSize;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * 저장소에 올린 파일을 제보에 첨부한다.
     *
     * @param leagueId 리그 ID
     * @param reportId 제보 ID
     * @param sortOrder 첨부 순서 (0부터)
     * @param objectKey {@link Image#newObjectKey}로 만든 객체 키
     * @param originalFilename 원본 파일명 (표시용)
     * @param format 검증된 이미지 형식
     * @param fileSize 파일 크기 (바이트)
     * @param createdAt 업로드 일시
     * @return 저장 전 이미지
     */
    public static ReportImage attach(long leagueId, long reportId, int sortOrder,
                                     String objectKey, String originalFilename,
                                     ImageFormat format, long fileSize,
                                     LocalDateTime createdAt) {
        return new ReportImage(leagueId, reportId, sortOrder, objectKey, originalFilename,
                format.contentType(), fileSize, createdAt);
    }

    private static String truncate(String filename) {
        return filename.length() <= ORIGINAL_FILENAME_LENGTH
                ? filename : filename.substring(0, ORIGINAL_FILENAME_LENGTH);
    }
}
