package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import kr.ject.naspi.contents.domain.Image;
import kr.ject.naspi.contents.domain.ImageFormat;
import kr.ject.naspi.contents.domain.ImageRepository;
import kr.ject.naspi.contents.domain.ImageStorage;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    private static final long LEAGUE_ID = 3L;
    private static final long DISCLOSURE_ID = 5L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};
    private static final ImageFile PNG_FILE = new ImageFile("a.png", "image/png", PNG);

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ImageStorage imageStorage;

    private ImageService imageService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        imageService =
                new ImageService(imageRepository, imageStorage, clock, Duration.ofMinutes(5));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("공시 이미지 파일은 리그별 UUID 키로 저장소에 첨부 순서대로 올린다")
    void store() {
        List<StoredImage> stored = imageService.store(LEAGUE_ID, List.of(PNG_FILE, PNG_FILE));

        assertThat(stored).extracting(StoredImage::format, StoredImage::fileSize)
                .containsOnly(tuple(ImageFormat.PNG,
                        (long) PNG.length));
        assertThat(stored).extracting(StoredImage::objectKey)
                .allMatch(key -> key.startsWith("images/leagues/3/"))
                .doesNotHaveDuplicates();
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        then(imageStorage).should(times(2)).upload(key.capture(), eq(PNG), eq("image/png"));
        assertThat(key.getAllValues())
                .containsExactlyElementsOf(stored.stream().map(StoredImage::objectKey).toList());
        then(imageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미지가 없으면 아무것도 올리지 않는다")
    void storeEmpty() {
        assertThat(imageService.store(LEAGUE_ID, List.of())).isEmpty();
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공시 1건에 이미지는 최대 4개까지 첨부할 수 있다")
    void storeCountLimit() {
        assertThat(imageService.store(LEAGUE_ID, Collections.nCopies(4, PNG_FILE))).hasSize(4);
        assertBiz(() -> imageService.store(LEAGUE_ID, Collections.nCopies(5, PNG_FILE)),
                ErrorCode.IMAGE_COUNT_EXCEEDED);
    }

    @Test
    @DisplayName("이미지는 1장 최대 5MB이며 빈 파일은 거절한다")
    void storeSizeLimit() {
        byte[] tooLarge = new byte[(int) Image.MAX_SIZE_BYTES + 1];
        System.arraycopy(PNG, 0, tooLarge, 0, PNG.length);

        assertBiz(() -> imageService.store(LEAGUE_ID,
                        List.of(new ImageFile("a.png", "image/png", tooLarge))),
                ErrorCode.INVALID_IMAGE_SIZE);
        assertBiz(() -> imageService.store(LEAGUE_ID,
                        List.of(new ImageFile("a.png", "image/png", new byte[0]))),
                ErrorCode.INVALID_IMAGE_SIZE);
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("JPEG, PNG가 아닌 파일이 하나라도 있으면 아무것도 올리지 않는다")
    void storeInvalidFormat() {
        assertBiz(() -> imageService.store(LEAGUE_ID,
                        List.of(PNG_FILE, new ImageFile("a.gif", "image/gif", PNG))),
                ErrorCode.INVALID_IMAGE_FORMAT);
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("파일을 올리는 중간에 실패하면 이미 올린 파일을 지우고 오류를 그대로 던진다")
    void storePartialFailure() {
        AtomicInteger calls = new AtomicInteger();
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        // 첫 번째 업로드는 성공, 두 번째에서 실패
        willAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                throw new NaspiRuntimeException("gcs down");
            }
            return null;
        }).given(imageStorage).upload(key.capture(), any(), anyString());

        assertThatThrownBy(() -> imageService.store(LEAGUE_ID, List.of(PNG_FILE, PNG_FILE)))
                .isInstanceOf(NaspiRuntimeException.class);

        then(imageStorage).should().delete(key.getAllValues().get(0));
        then(imageStorage).should(never()).delete(key.getAllValues().get(1));
    }

    @Test
    @DisplayName("올린 파일을 첨부 순서대로 공시에 첨부(메타데이터 저장)한다")
    void attach() {
        AtomicLong ids = new AtomicLong(10);
        given(imageRepository.saveAll(anyList())).willAnswer(invocation -> {
            List<Image> images = invocation.getArgument(0);
            images.forEach(
                    image -> ReflectionTestUtils.setField(image, "id", ids.incrementAndGet()));
            return images;
        });
        List<StoredImage> stored = List.of(
                new StoredImage("k1", "a.png", ImageFormat.PNG, 9),
                new StoredImage("k2", "b.jpg", ImageFormat.JPEG, 7));

        assertThat(imageService.attach(LEAGUE_ID, DISCLOSURE_ID, stored)).containsExactly(11L, 12L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Image>> saved = ArgumentCaptor.forClass(List.class);
        then(imageRepository).should().saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(Image::getLeagueId, Image::getDisclosureId, Image::getSortOrder,
                        Image::getObjectKey, Image::getContentType, Image::getCreatedAt)
                .containsExactly(
                        tuple(LEAGUE_ID, DISCLOSURE_ID, 0, "k1",
                                "image/png", NOW),
                        tuple(LEAGUE_ID, DISCLOSURE_ID, 1, "k2",
                                "image/jpeg", NOW));
    }

    @Test
    @DisplayName("공시에 첨부하지 못한 파일은 지우고, 삭제가 실패해도 나머지를 계속 지운다")
    void discard() {
        willThrow(new NaspiRuntimeException("gcs down")).given(imageStorage).delete("k1");

        imageService.discard(List.of(new StoredImage("k1", "a.png", ImageFormat.PNG, 9),
                new StoredImage("k2", "b.png", ImageFormat.PNG, 9)));

        then(imageStorage).should().delete("k2");
    }

    @Test
    @DisplayName("공시 이미지의 서명 URL을 첨부 순서대로 발급하고 만료 일시를 함께 준다")
    void issueUrls() {
        Image first = stored(11L, 0);
        Image second = stored(12L, 1);
        given(imageRepository.findAllByDisclosureIdOrderBySortOrderAsc(DISCLOSURE_ID))
                .willReturn(List.of(first, second));
        given(imageStorage.signedUrl(first.getObjectKey(), Duration.ofMinutes(5)))
                .willReturn("https://signed/1");
        given(imageStorage.signedUrl(second.getObjectKey(), Duration.ofMinutes(5)))
                .willReturn("https://signed/2");

        assertThat(imageService.issueUrls(DISCLOSURE_ID)).containsExactly(
                new ImageUrl(11L, "https://signed/1", NOW.plusMinutes(5)),
                new ImageUrl(12L, "https://signed/2", NOW.plusMinutes(5)));
    }

    @Test
    @DisplayName("공시를 삭제하면 이미지 메타데이터를 먼저 지우고, 파일 삭제가 실패해도 나머지를 계속 지운다")
    void deleteAll() {
        Image first = stored(11L, 0);
        Image second = stored(12L, 1);
        List<Image> images = List.of(first, second);
        given(imageRepository.findAllByDisclosureIdOrderBySortOrderAsc(DISCLOSURE_ID))
                .willReturn(images);
        willThrow(new NaspiRuntimeException("gcs down")).given(imageStorage)
                .delete(first.getObjectKey());

        imageService.deleteAll(DISCLOSURE_ID);

        then(imageRepository).should().deleteAll(images);
        then(imageStorage).should().delete(second.getObjectKey());
    }

    @Test
    @DisplayName("트랜잭션 안에서 공시 이미지를 삭제하면 파일은 커밋된 뒤에 지운다")
    void deleteAllAfterCommit() {
        Image image = stored(11L, 0);
        given(imageRepository.findAllByDisclosureIdOrderBySortOrderAsc(DISCLOSURE_ID))
                .willReturn(List.of(image));
        TransactionSynchronizationManager.initSynchronization();

        imageService.deleteAll(DISCLOSURE_ID);

        then(imageStorage).should(never()).delete(anyString());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        then(imageStorage).should().delete(image.getObjectKey());
    }

    private static Image stored(long id, int sortOrder) {
        Image image = Image.attach(LEAGUE_ID, DISCLOSURE_ID, sortOrder,
                Image.newObjectKey(LEAGUE_ID, ImageFormat.PNG), "a.png", ImageFormat.PNG, 9, NOW);
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    private static void assertBiz(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(NaspiBizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
