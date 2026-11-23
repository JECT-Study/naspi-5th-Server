package kr.ject.naspi.contents.application;

import java.util.List;
import java.util.Map;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import kr.ject.naspi.contents.domain.NewsRepository;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import org.springframework.stereotype.Service;

/**
 * 공시 목록·상세 조회.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "공시"·"삭제"): 리그 참여자는 종목에 등록된 공시를 볼 수 있고,
 * 삭제된 공시 원문은 볼 수 없다. 결정 (docs/issues/v1/contents.md CNT-02): 목록은 종목별 최신순 커서 페이징이고,
 * 이미지는 목록에서 첫 장만, 상세에서 전부 서명 URL로 준다. 리그가 끝난 뒤에도 조회할 수 있다.
 *
 * <p>이미지 서명 URL 발급(운영에서는 IAM API 호출)이 DB 커넥션을 잡고 있지 않도록 트랜잭션으로 묶지 않는다.
 * 저장소 호출은 각각 읽기만 한다.
 */
@Service
public class DisclosureQueryService {

    /** 한 페이지 기본 개수. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 한 페이지 최대 개수. */
    public static final int MAX_PAGE_SIZE = 50;

    private final DisclosureRepository disclosureRepository;
    private final ImageService imageService;
    private final DisclosureReactionService reactionService;
    private final LeagueQueryService leagueQueryService;
    private final NewsRepository newsRepository;

    /**
     * 공시 조회 서비스를 만든다.
     *
     * @param disclosureRepository 공시 저장소
     * @param imageService 이미지 서명 URL 발급
     * @param reactionService 반응 집계
     * @param leagueQueryService 플레이어 조회
     * @param newsRepository 뉴스 저장소
     */
    public DisclosureQueryService(DisclosureRepository disclosureRepository,
                                  ImageService imageService,
                                  DisclosureReactionService reactionService,
                                  LeagueQueryService leagueQueryService,
                                  NewsRepository newsRepository) {
        this.disclosureRepository = disclosureRepository;
        this.imageService = imageService;
        this.reactionService = reactionService;
        this.leagueQueryService = leagueQueryService;
        this.newsRepository = newsRepository;
    }

    /**
     * 종목의 공시를 최신순으로 한 페이지 조회한다. 삭제된 공시는 빠진다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param cursor 이전 페이지의 {@link DisclosurePage#nextCursor()}. 첫 페이지면 null
     * @param size 한 페이지 개수 (1~{@value #MAX_PAGE_SIZE})
     * @return 공시 목록 한 페이지
     * @throws NaspiBizException 리그에 입장하지 않은 회원인 경우
     */
    public DisclosurePage findByStock(long memberId, long leagueId, long stockId, Long cursor,
                                      int size) {
        long playerId = viewerPlayerId(memberId, leagueId);
        // 다음 페이지가 있는지 알기 위해 1개 더 읽는다
        List<Disclosure> fetched = disclosureRepository.findPublishedByStock(leagueId, stockId,
                cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<Disclosure> disclosures = hasNext ? fetched.subList(0, size) : fetched;
        List<Long> ids = disclosures.stream().map(Disclosure::getId).toList();

        // 이미지·반응은 공시마다 따로 조회하지 않고 페이지 단위로 한 번에 읽는다
        Map<Long, List<ImageUrl>> images = imageService.issueUrls(ids);
        Map<Long, DisclosureReactionSummary> reactions =
                reactionService.summarizeAll(ids, playerId);
        // TODO: 댓글(CNT-09)을 구현하면 공시마다 댓글이 있을 경우 최대 1개를 함께 담는다.
        //       공시마다 따로 조회하지 말고 이미지·반응처럼 페이지 단위로 한 번에 읽는다.

        List<DisclosureListItem> items = disclosures.stream()
                .map(d -> new DisclosureListItem(d.getId(), d.getStockId(),
                        d.getTitle().getValue(), d.getBody().getValue(), d.getTradingDay(),
                        d.getCreatedAt(), d.getAuthorPlayerId(),
                        d.getAuthorPlayerId() == playerId,
                        images.getOrDefault(d.getId(), List.of()),
                        reactions.get(d.getId())))
                .toList();
        Long nextCursor = hasNext ? ids.get(ids.size() - 1) : null;
        return new DisclosurePage(items, nextCursor);
    }

    /**
     * 공시 상세를 조회한다. 공시로 만든 AI 뉴스를 생성 상태와 함께 준다.
     *
     * <p>TODO: 공시가 삭제돼도 이미 발행된 뉴스는 볼 수 있어야 하므로, 삭제된 공시의 뉴스는 뉴스 쪽 API(CNT-08)로
     *       제공한다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @return 공시 상세
     * @throws NaspiBizException 리그에 입장하지 않았거나, 공시가 없거나 삭제된 경우
     */
    public DisclosureDetail getDetail(long memberId, long leagueId, long stockId,
                                      long disclosureId) {
        long playerId = viewerPlayerId(memberId, leagueId);
        // 경로의 리그·종목에 속하고 삭제되지 않은 공시만 보여준다 (삭제된 공시 원문은 조회 불가)
        Disclosure d = disclosureRepository.findById(disclosureId)
                .filter(found -> found.getLeagueId() == leagueId
                        && found.getStockId() == stockId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new NaspiBizException(ErrorCode.DISCLOSURE_NOT_FOUND));
        return new DisclosureDetail(d.getId(), d.getStockId(), d.getTitle().getValue(),
                d.getBody().getValue(), d.getTradingDay(), d.getCreatedAt(),
                d.getAuthorPlayerId(), d.getAuthorPlayerId() == playerId,
                imageService.issueUrls(d.getId()),
                reactionService.summarizeAll(List.of(d.getId()), playerId).get(d.getId()),
                newsRepository.findByDisclosureId(d.getId()).map(DisclosureNews::from)
                        .orElse(null));
    }

    /** 리그 참여자만 공시를 볼 수 있다. 리그 상태는 따지지 않아 종료된 리그도 조회할 수 있다. */
    private long viewerPlayerId(long memberId, long leagueId) {
        return leagueQueryService.findPlayerId(memberId, leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));
    }
}
