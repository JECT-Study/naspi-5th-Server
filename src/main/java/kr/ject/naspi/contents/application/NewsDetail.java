package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.NewsType;

/**
 * 발행된 뉴스 상세. 목록 항목({@link NewsListItem})과 같은 필드에서 가장 먼저 등록된 댓글 대신 댓글 전체를 주고,
 * 원본 공시가 더해진다.
 *
 * @param newsId 뉴스 ID
 * @param stockId 종목 ID
 * @param newsType 뉴스 유형 (공시 기반 DISCLOSURE, 사용자 제보 기반 TIP)
 * @param title 제목
 * @param body 본문
 * @param sentiment 호재(POSITIVE)·악재(NEGATIVE)
 * @param tradingDay 원본 공시를 작성한 거래일 번호 (1~7)
 * @param createdAt 뉴스 생성을 요청한 일시 (공시 작성 일시, KST)
 * @param publishedAt 발행 일시 (KST)
 * @param comments 댓글 전체 (오래된 순). 댓글이 없으면 빈 목록
 * @param disclosure 원본 공시. 공시 없이 만든 뉴스이거나 공시가 삭제됐으면 null
 */
public record NewsDetail(long newsId, long stockId, NewsType newsType, String title, String body,
                         NewsSentiment sentiment, int tradingDay, LocalDateTime createdAt,
                         LocalDateTime publishedAt, List<CommentItem> comments,
                         NewsDisclosure disclosure) {
}
