package kr.ject.naspi.contents.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsRepository;
import kr.ject.naspi.contents.domain.NewsStatus;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import org.springframework.stereotype.Service;

/**
 * 종목별 뉴스 목록·상세 조회. 공시 조회({@link DisclosureQueryService})와 같은 구조다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "뉴스 - 공시 기반 AI 뉴스"): 뉴스는 종목에 연결되고 삭제할 수
 * 없으며, 공시가 삭제돼도 발행된 뉴스는 유지된다. 발행(PUBLISHED)된 뉴스만 보이고, 리그 참여자만 볼 수 있으며
 * 종료된 리그도 조회할 수 있다. 결정 (docs/issues/v1/contents.md CNT-08): 목록에는 댓글 수와 가장 먼저 등록된 댓글을,
 * 상세에는 댓글 전체를 주고, 원본 공시는 상세에만 준다. 삭제된 공시 원문은 조회할 수 없으므로
 * 원본 공시가 삭제됐으면 공시 없이 만든 뉴스처럼 비워 둔다.
 */
@Service
public class NewsQueryService {

    /** 한 페이지 기본 개수. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 한 페이지 최대 개수. */
    public static final int MAX_PAGE_SIZE = 50;

    private final NewsRepository newsRepository;
    private final NewsCommentService commentService;
    private final LeagueQueryService leagueQueryService;
    private final DisclosureRepository disclosureRepository;
    private final StockQueryService stockQueryService;

    /**
     * 뉴스 조회 서비스를 만든다.
     *
     * @param newsRepository 뉴스 저장소
     * @param commentService 댓글 미리보기 조회
     * @param leagueQueryService 플레이어 조회
     * @param disclosureRepository 공시 저장소
     * @param stockQueryService 종목 이름 조회
     */
    public NewsQueryService(NewsRepository newsRepository, NewsCommentService commentService,
                            LeagueQueryService leagueQueryService,
                            DisclosureRepository disclosureRepository,
                            StockQueryService stockQueryService) {
        this.newsRepository = newsRepository;
        this.commentService = commentService;
        this.leagueQueryService = leagueQueryService;
        this.disclosureRepository = disclosureRepository;
        this.stockQueryService = stockQueryService;
    }

    /**
     * 종목의 발행된 뉴스를 발행 일시 최신순(같으면 ID 내림차순)으로 한 페이지 조회한다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param cursor 이전 페이지의 {@link NewsPage#nextCursor()}. 첫 페이지면 null
     * @param size 한 페이지 개수 (1~{@value #MAX_PAGE_SIZE})
     * @return 뉴스 목록 한 페이지
     * @throws NaspiBizException 리그에 입장하지 않았거나, 종료 시각이 지난 리그이거나, 커서 형식이 올바르지 않은 경우
     */
    public NewsPage findByStock(long memberId, long leagueId, long stockId, String cursor,
                                int size) {
        long playerId = viewerPlayerId(memberId, leagueId);
        ContentCursor after = ContentCursor.decode(cursor, FeedItemType.NEWS);
        // 다음 페이지가 있는지 알기 위해 1개 더 읽는다
        List<News> fetched = newsRepository.findPublishedInLeague(leagueId, stockId,
                after == null ? null : after.at(), after == null ? Long.MAX_VALUE : after.id(),
                size + 1);
        boolean hasNext = fetched.size() > size;
        List<News> news = hasNext ? fetched.subList(0, size) : fetched;
        List<NewsListItem> items = toListItems(leagueId, playerId, news);
        String nextCursor = hasNext ? ContentCursor.of(news.get(news.size() - 1)).encode() : null;
        return new NewsPage(items, nextCursor);
    }

    /**
     * 뉴스를 목록 항목으로 바꾼다. 통합 피드({@link FeedQueryService})도 같은 항목을 쓴다.
     *
     * @param leagueId 리그 ID
     * @param playerId 조회하는 플레이어 ID
     * @param news 뉴스 목록
     * @return 목록 항목 (뉴스 순서 유지)
     */
    List<NewsListItem> toListItems(long leagueId, long playerId, List<News> news) {
        List<Long> ids = news.stream().map(News::getId).toList();

        // 댓글은 뉴스마다 따로 조회하지 않고 페이지 단위로 한 번에 읽는다
        Map<Long, CommentPreview> comments = commentService.previews(leagueId, ids, playerId);
        Map<Long, String> stockNames = stockQueryService.findAllByLeagueId(leagueId).stream()
                .collect(Collectors.toMap(LeagueStock::stockId, LeagueStock::name));

        return news.stream()
                .map(n -> new NewsListItem(n.getId(), n.getStockId(),
                        stockNames.get(n.getStockId()), n.getTitle(), n.getBody(),
                        n.getSentiment(), n.getTradingDay(), n.getCreatedAt(),
                        n.getPublishedAt(), comments.get(n.getId())))
                .toList();
    }

    /**
     * 발행된 뉴스 상세를 조회한다. 원본 공시를 함께 준다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param newsId 뉴스 ID
     * @return 뉴스 상세
     * @throws NaspiBizException 리그에 입장하지 않았거나, 종료 시각이 지난 리그이거나, 뉴스가 없거나 발행되지 않은 경우
     */
    public NewsDetail getDetail(long memberId, long leagueId, long stockId, long newsId) {
        long playerId = viewerPlayerId(memberId, leagueId);
        // 경로의 리그·종목에 속하고 발행된 뉴스만 보여준다
        News n = newsRepository.findById(newsId)
                .filter(found -> found.getLeagueId() == leagueId
                        && found.getStockId() == stockId)
                .filter(found -> found.getStatus() == NewsStatus.PUBLISHED)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.NEWS_NOT_FOUND));
        return new NewsDetail(n.getId(), n.getStockId(), n.getTitle(), n.getBody(),
                n.getSentiment(), n.getTradingDay(), n.getCreatedAt(), n.getPublishedAt(),
                commentService.findAll(leagueId, n.getId(), playerId),
                findDisclosure(n));
    }

    /** 원본 공시. 공시 없이 만든 뉴스이거나 공시가 삭제됐으면(원문 조회 불가) null. */
    private NewsDisclosure findDisclosure(News news) {
        if (news.getDisclosureId() == null) {
            return null;
        }
        return disclosureRepository.findById(news.getDisclosureId())
                .filter(d -> !d.isDeleted())
                .map(NewsDisclosure::from)
                .orElse(null);
    }

    /** 리그 참여자만 뉴스를 볼 수 있다. 종료 시각이 지난 리그는 결과 페이지 외에 접근할 수 없어 조회할 수 없다. */
    private long viewerPlayerId(long memberId, long leagueId) {
        return leagueQueryService.requireAccessiblePlayer(memberId, leagueId);
    }
}
