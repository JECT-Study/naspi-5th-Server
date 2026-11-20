package kr.ject.naspi.contents.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.ject.naspi.contents.domain.Image;
import kr.ject.naspi.contents.domain.ImageFormat;
import kr.ject.naspi.contents.domain.ImageRepository;
import kr.ject.naspi.contents.domain.ImageStorage;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 공시 이미지 업로드·서명 URL 발급·삭제. 별도 API 없이 공시 작성·조회·삭제에서 호출한다.
 * 권한(공시 작성자, 같은 리그 플레이어 등)은 호출하는 공시 쪽에서 먼저 확인한다.
 *
 * <p>파일은 {@link ImageStorage}, 메타데이터는 DB에 두며 둘은 한 트랜잭션으로 묶을 수 없다. 그래서 저장소 호출
 * 동안 DB 트랜잭션을 열어 두지 않고, 어긋날 때는 사용자에게 보이는 쪽(DB)을 기준으로 맞춘다.
 * <ul>
 *   <li>작성: {@link #store}로 모든 파일을 검증한 뒤 저장소에 올리고(트랜잭션 밖), 공시를 저장하는 트랜잭션
 *       안에서 {@link #attach}로 메타데이터를 저장한다. 공시 저장이 실패하면 {@link #discard}로 파일을 지운다 (보상).
 *       자동 재시도는 하지 않고 오류로 응답하며, 다시 올리면 새 키로 저장된다.</li>
 *   <li>삭제: 메타데이터 삭제 → 커밋 후 파일 삭제. 파일 삭제가 실패해도 더 이상 조회되지 않으므로 성공으로 보고
 *       남은 객체 키를 경고 로그로 남긴다 (수동 정리 대상).</li>
 * </ul>
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "공시"): JPEG·PNG, 공시 1건에 최대 4개, 1장 최대 5MB.
 *
 * <p>TODO: 공시 조회(CNT-02)에서 {@link #issueUrls}를, 공시 삭제(CNT-03)에서 {@link #deleteAll}을 호출하도록 연동한다.
 */
@Slf4j
@Service
public class ImageService {

    private final ImageRepository imageRepository;
    private final ImageStorage imageStorage;
    private final Clock clock;
    private final Duration signedUrlExpiration;

    /**
     * 이미지 서비스를 만든다.
     *
     * @param imageRepository 메타데이터 저장소
     * @param imageStorage 파일 저장소
     * @param clock 현재 시각
     * @param signedUrlExpiration 다운로드 URL 유효 시간 ({@code naspi.image.signed-url-expiration})
     */
    public ImageService(ImageRepository imageRepository, ImageStorage imageStorage, Clock clock,
                        @Value("${naspi.image.signed-url-expiration:5m}")
                        Duration signedUrlExpiration) {
        this.imageRepository = imageRepository;
        this.imageStorage = imageStorage;
        this.clock = clock;
        this.signedUrlExpiration = signedUrlExpiration;
    }

    /**
     * 이미지 파일을 검증하고 저장소에 올린다. 하나라도 규칙에 맞지 않으면 아무것도 올리지 않고, 올리는 중에
     * 실패하면 이미 올린 파일을 지운다. DB 트랜잭션 밖에서 호출한다.
     *
     * @param leagueId 리그 ID
     * @param files 첨부할 이미지 (첨부 순서대로, 없으면 빈 목록)
     * @return 올린 파일 (첨부 순서대로)
     * @throws NaspiBizException 4개를 넘거나, 크기·형식이 맞지 않는 파일이 있는 경우
     */
    public List<StoredImage> store(long leagueId, List<ImageFile> files) {
        if (files.size() > Image.MAX_COUNT_PER_DISCLOSURE) {
            throw new NaspiBizException(ErrorCode.IMAGE_COUNT_EXCEEDED);
        }
        List<ImageFormat> formats = files.stream().map(ImageService::validate).toList();

        List<StoredImage> stored = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                ImageFile file = files.get(i);
                ImageFormat format = formats.get(i);
                String objectKey = Image.newObjectKey(leagueId, format);
                imageStorage.upload(objectKey, file.content(), format.contentType());
                stored.add(new StoredImage(objectKey, file.originalFilename(), format,
                        file.content().length));
            }
            return stored;
        } catch (RuntimeException e) {
            discard(stored);
            throw e;
        }
    }

    /**
     * 저장소에 올린 파일을 공시에 첨부한다(메타데이터 저장). 공시를 저장하는 트랜잭션 안에서 호출한다.
     *
     * @param leagueId 리그 ID
     * @param disclosureId 공시 ID
     * @param stored {@link #store}가 돌려준 파일 (첨부 순서대로)
     * @return 저장된 이미지 ID (첨부 순서대로)
     */
    public List<Long> attach(long leagueId, long disclosureId, List<StoredImage> stored) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Image> images = IntStream.range(0, stored.size())
                .mapToObj(i -> Image.attach(leagueId, disclosureId, i, stored.get(i).objectKey(),
                        stored.get(i).originalFilename(), stored.get(i).format(),
                        stored.get(i).fileSize(), now))
                .toList();
        return imageRepository.saveAll(images).stream().map(Image::getId).toList();
    }

    /**
     * 공시에 첨부하지 못한 파일을 지운다 (공시 저장 실패 시 보상). 삭제 실패는 경고 로그만 남긴다.
     *
     * @param stored {@link #store}가 돌려준 파일
     */
    public void discard(List<StoredImage> stored) {
        stored.forEach(image -> deleteQuietly(image.objectKey()));
    }

    /**
     * 공시 이미지의 서명 URL을 첨부 순서대로 발급한다. URL은 DB에 저장하지 않고 요청할 때마다 새로 만든다.
     *
     * <p>GKE에서는 URL 1개마다 IAM {@code signBlob} 호출이 일어나므로, 공시 목록처럼 많이 발급할 때는 비용을 고려한다.
     *
     * @param disclosureId 공시 ID
     * @return 서명 URL 목록, 이미지가 없으면 빈 목록
     */
    public List<ImageUrl> issueUrls(long disclosureId) {
        // 서명 전에 계산해 실제 만료보다 늦은 시각을 알려주지 않게 한다.
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(signedUrlExpiration);
        return imageRepository.findAllByDisclosureIdOrderBySortOrderAsc(disclosureId).stream()
                .map(image -> new ImageUrl(image.getId(),
                        imageStorage.signedUrl(image.getObjectKey(), signedUrlExpiration),
                        expiresAt))
                .toList();
    }

    /**
     * 공시 목록용 이미지 미리보기를 공시별로 만든다. 서명 URL은 첫 이미지에만 발급해 IAM {@code signBlob} 호출을
     * 공시 1건당 최대 1번으로 줄인다 (docs/issues/v1/contents.md CNT-02 결정).
     *
     * @param disclosureIds 공시 ID 목록
     * @return 공시 ID별 미리보기. 이미지가 없는 공시는 담지 않는다
     */
    public Map<Long, ImagePreview> previews(Collection<Long> disclosureIds) {
        // 빈 페이지면 IN () 쿼리를 보내지 않는다
        if (disclosureIds.isEmpty()) {
            return Map.of();
        }
        // 만료 시각 계산
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(signedUrlExpiration);
        // 공시 목록의 이미지는 첫 장만 보여주므로 서명 URL도 첫 장에만 발급하고, 나머지는 개수만 알려준다
        Map<Long, List<Image>> byDisclosure = imageRepository
                .findAllByDisclosureIdInOrderByDisclosureIdAscSortOrderAsc(disclosureIds).stream()
                .collect(Collectors.groupingBy(Image::getDisclosureId));
        Map<Long, ImagePreview> previews = new HashMap<>();
        byDisclosure.forEach((disclosureId, images) -> {
            // 첫번째 이미지에만 서명 URL 발급
            Image first = images.get(0);
            previews.put(disclosureId, new ImagePreview(images.size(), new ImageUrl(first.getId(),
                    imageStorage.signedUrl(first.getObjectKey(), signedUrlExpiration),
                    expiresAt)));
        });
        return previews;
    }

    /**
     * 공시에 첨부한 이미지를 모두 삭제한다. 공시를 삭제할 때 호출한다.
     *
     * <p>트랜잭션 안에서 호출하면 파일은 커밋된 뒤에 지운다. 공시 삭제가 롤백되면 파일도 남아 이미지가 깨지지 않는다.
     *
     * @param disclosureId 공시 ID
     */
    public void deleteAll(long disclosureId) {
        List<Image> images = imageRepository.findAllByDisclosureIdOrderBySortOrderAsc(disclosureId);
        imageRepository.deleteAll(images);
        List<String> objectKeys = images.stream().map(Image::getObjectKey).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            objectKeys.forEach(ImageService.this::deleteQuietly);
                        }
                    });
        } else {
            objectKeys.forEach(this::deleteQuietly);
        }
    }

    private static ImageFormat validate(ImageFile file) {
        byte[] content = file.content();
        if (content == null || content.length == 0 || content.length > Image.MAX_SIZE_BYTES) {
            throw new NaspiBizException(ErrorCode.INVALID_IMAGE_SIZE);
        }
        return ImageFormat.detect(file.originalFilename(), file.contentType(), content)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.INVALID_IMAGE_FORMAT));
    }

    /** 이미 DB 기준으로 처리가 끝났으므로 파일 삭제 실패는 결과를 바꾸지 않고 정리 대상으로만 남긴다. */
    private void deleteQuietly(String objectKey) {
        try {
            imageStorage.delete(objectKey);
        } catch (RuntimeException e) {
            log.warn("이미지 파일 삭제 실패, 수동 정리 필요: objectKey={}", objectKey, e);
        }
    }
}
