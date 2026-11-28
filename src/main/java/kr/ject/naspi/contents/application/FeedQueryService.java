package kr.ject.naspi.contents.application;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsRepository;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import org.springframework.stereotype.Service;

/**
 * 리그의 공시와 발행된 뉴스를 한 목록으로 합친 통합 피드 조회.
 *
 * <p>공시는 작성 일시, 뉴스는 발행 일시를 등록 일시로 보고 최신순으로 정렬한다. 항목은 종목별 공시 목록
 * ({@link DisclosureQueryService})·뉴스 목록({@link NewsQueryService})의 항목과 같다. 보이는 범위(삭제된 공시 제외,
 * 발행된 뉴스만, 리그 참여자만, 종료된 리그도 조회 가능)도 각 목록과 같다.
 *
 * <p>공시 목록과 같은 이유(이미지 서명 URL 발급)로 트랜잭션으로 묶지 않는다.
 */
@Service
public class FeedQueryService {

    /** 한 페이지 기본 개수. */
    public static final int DEFAULT_PAGE_SIZE = 5;

    /** 한 페이지 최대 개수. */
    public static final int MAX_PAGE_SIZE = 50;

    private final DisclosureRepository disclosureRepository;
    private final NewsRepository newsRepository;
    private final DisclosureQueryService disclosureQueryService;
    private final NewsQueryService newsQueryService;
    private final LeagueQueryService leagueQueryService;

    /**
     * 통합 피드 조회 서비스를 만든다.
     *
     * @param disclosureRepository 공시 저장소
     * @param newsRepository 뉴스 저장소
     * @param disclosureQueryService 공시 목록 항목 변환
     * @param newsQueryService 뉴스 목록 항목 변환
     * @param leagueQueryService 플레이어 조회
     */
    public FeedQueryService(DisclosureRepository disclosureRepository,
                            NewsRepository newsRepository,
                            DisclosureQueryService disclosureQueryService,
                            NewsQueryService newsQueryService,
                            LeagueQueryService leagueQueryService) {
        this.disclosureRepository = disclosureRepository;
        this.newsRepository = newsRepository;
        this.disclosureQueryService = disclosureQueryService;
        this.newsQueryService = newsQueryService;
        this.leagueQueryService = leagueQueryService;
    }

    /**
     * 리그의 공시·뉴스를 등록 일시 최신순으로 한 페이지 조회한다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID. null이면 리그의 모든 종목
     * @param type 항목 종류. null이면 공시와 뉴스 모두
     * @param cursor 이전 페이지의 {@link FeedPage#nextCursor()}. 첫 페이지면 null
     * @param size 한 페이지 개수 (1~{@value #MAX_PAGE_SIZE})
     * @return 통합 피드 한 페이지
     * @throws NaspiBizException 리그에 입장하지 않았거나, 종료 시각이 지난 리그이거나, 커서 형식이 올바르지 않은 경우
     */
    public FeedPage find(long memberId, long leagueId, Long stockId, FeedItemType type,
                         String cursor, int size) {
        final long playerId = leagueQueryService.requireAccessiblePlayer(memberId, leagueId);
        ContentCursor after = ContentCursor.decode(cursor);

        // 종류별로 다음 페이지 여부를 알 만큼(size + 1) 읽어 합친 뒤 앞에서부터 자른다
        List<Disclosure> disclosures = type == FeedItemType.NEWS ? List.of()
                : disclosureRepository.findPublishedInLeague(leagueId, stockId,
                        after == null ? null : after.at(),
                        after == null ? Long.MAX_VALUE : after.idBefore(FeedItemType.DISCLOSURE),
                        size + 1);
        List<News> news = type == FeedItemType.DISCLOSURE ? List.of()
                : newsRepository.findPublishedInLeague(leagueId, stockId,
                        after == null ? null : after.at(),
                        after == null ? Long.MAX_VALUE : after.idBefore(FeedItemType.NEWS),
                        size + 1);

        List<ContentCursor> merged = new ArrayList<>();
        disclosures.forEach(d -> merged.add(ContentCursor.of(d)));
        news.forEach(n -> merged.add(ContentCursor.of(n)));
        merged.sort(ContentCursor.ORDER);
        boolean hasNext = merged.size() > size;
        List<ContentCursor> page = hasNext ? merged.subList(0, size) : merged;

        List<FeedItem> items = toItems(leagueId, playerId, page, disclosures, news);
        String nextCursor = hasNext ? page.get(page.size() - 1).encode() : null;
        return new FeedPage(items, nextCursor);
    }

    /** 페이지에 들어간 공시·뉴스만 종류별로 한 번에 목록 항목으로 바꾸고 피드 순서대로 놓는다. */
    private List<FeedItem> toItems(long leagueId, long playerId, List<ContentCursor> page,
                                   List<Disclosure> disclosures, List<News> news) {
        List<Disclosure> pageDisclosures = pick(page, FeedItemType.DISCLOSURE, disclosures,
                Disclosure::getId);
        List<News> pageNews = pick(page, FeedItemType.NEWS, news, News::getId);
        Iterator<DisclosureListItem> disclosureItems = disclosureQueryService
                .toListItems(leagueId, playerId, pageDisclosures).iterator();
        Iterator<NewsListItem> newsItems = newsQueryService
                .toListItems(leagueId, playerId, pageNews).iterator();

        // 종류별 목록은 각각 피드 순서와 같은 순서이므로 피드 순서대로 하나씩 꺼낸다
        return page.stream()
                .map(position -> switch (position.type()) {
                    case DISCLOSURE -> (FeedItem) disclosureItems.next();
                    case NEWS -> newsItems.next();
                })
                .toList();
    }

    /** 페이지에 들어간 {@code type} 항목을 피드 순서대로 고른다. */
    private static <T> List<T> pick(List<ContentCursor> page, FeedItemType type, List<T> fetched,
                                    Function<T, Long> id) {
        Map<Long, T> byId = fetched.stream()
                .collect(Collectors.toMap(id, Function.identity()));
        return page.stream()
                .filter(position -> position.type() == type)
                .map(position -> byId.get(position.id()))
                .toList();
    }
}
