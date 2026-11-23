package kr.ject.naspi.contents.application;

import java.util.List;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsRepository;
import kr.ject.naspi.contents.domain.NewsStatus;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import org.springframework.stereotype.Service;

/**
 * 종목별 뉴스 목록·상세 조회.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "뉴스 - 공시 기반 AI 뉴스"): 뉴스는 종목에 연결되고 삭제할 수
 * 없으며, 공시가 삭제돼도 발행된 뉴스는 유지된다(공시 테이블을 보지 않는다). 발행(PUBLISHED)된 뉴스만 보이고,
 * 리그 참여자만 볼 수 있으며 종료된 리그도 조회할 수 있다.
 */
@Service
public class NewsQueryService {

    /** 한 페이지 기본 개수. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 한 페이지 최대 개수. */
    public static final int MAX_PAGE_SIZE = 50;

    private final NewsRepository newsRepository;
    private final LeagueQueryService leagueQueryService;

    /**
     * 뉴스 조회 서비스를 만든다.
     *
     * @param newsRepository 뉴스 저장소
     * @param leagueQueryService 플레이어 조회
     */
    public NewsQueryService(NewsRepository newsRepository,
                            LeagueQueryService leagueQueryService) {
        this.newsRepository = newsRepository;
        this.leagueQueryService = leagueQueryService;
    }

    /**
     * 종목의 발행된 뉴스를 최신순으로 한 페이지 조회한다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param cursor 이전 페이지의 {@link NewsPage#nextCursor()}. 첫 페이지면 null
     * @param size 한 페이지 개수 (1~{@value #MAX_PAGE_SIZE})
     * @return 뉴스 목록 한 페이지
     * @throws NaspiBizException 리그에 입장하지 않은 회원인 경우
     */
    public NewsPage findByStock(long memberId, long leagueId, long stockId, Long cursor,
                                int size) {
        checkViewer(memberId, leagueId);
        // 다음 페이지가 있는지 알기 위해 1개 더 읽는다
        List<News> fetched = newsRepository.findPublishedByStock(leagueId, stockId, cursor,
                size + 1);
        boolean hasNext = fetched.size() > size;
        List<News> news = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? news.get(news.size() - 1).getId() : null;
        return new NewsPage(news.stream().map(NewsItem::from).toList(), nextCursor);
    }

    /**
     * 발행된 뉴스 상세를 조회한다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param newsId 뉴스 ID
     * @return 뉴스 상세
     * @throws NaspiBizException 리그에 입장하지 않았거나, 뉴스가 없거나 발행되지 않은 경우
     */
    public NewsItem getDetail(long memberId, long leagueId, long stockId, long newsId) {
        checkViewer(memberId, leagueId);
        return newsRepository.findById(newsId)
                .filter(n -> n.getLeagueId() == leagueId && n.getStockId() == stockId
                        && n.getStatus() == NewsStatus.PUBLISHED)
                .map(NewsItem::from)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.NEWS_NOT_FOUND));
    }

    /** 리그 참여자만 뉴스를 볼 수 있다. 리그 상태는 따지지 않아 종료된 리그도 조회할 수 있다. */
    private void checkViewer(long memberId, long leagueId) {
        if (leagueQueryService.findPlayerId(memberId, leagueId).isEmpty()) {
            throw new NaspiBizException(ErrorCode.FORBIDDEN);
        }
    }
}
