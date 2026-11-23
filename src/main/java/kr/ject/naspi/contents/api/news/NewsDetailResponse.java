package kr.ject.naspi.contents.api.news;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.api.comment.CommentResponse;
import kr.ject.naspi.contents.application.NewsDetail;
import kr.ject.naspi.contents.domain.NewsSentiment;

/**
 * 뉴스 상세 응답. 목록 항목과 같은 필드에서 가장 최근 댓글 대신 댓글 전체를 주고, 원본 공시가 더해진다.
 *
 * @param newsId 뉴스 ID
 * @param stockId 종목 ID
 * @param title 제목
 * @param body 본문
 * @param sentiment 호재(POSITIVE)·악재(NEGATIVE)
 * @param tradingDay 원본 공시를 작성한 거래일 번호 (1~7)
 * @param createdAt 뉴스 생성 요청 일시 (KST)
 * @param publishedAt 발행 일시 (KST)
 * @param commentCount 댓글 수
 * @param comments 댓글 전체 (최신순). 댓글이 없으면 빈 목록
 * @param disclosure 원본 공시. 공시 없이 만든 뉴스이거나 공시가 삭제됐으면 null
 */
record NewsDetailResponse(long newsId, long stockId, String title, String body,
                          NewsSentiment sentiment, int tradingDay, LocalDateTime createdAt,
                          LocalDateTime publishedAt, long commentCount,
                          List<CommentResponse> comments, NewsDisclosureResponse disclosure) {

    static NewsDetailResponse from(NewsDetail detail) {
        return new NewsDetailResponse(detail.newsId(), detail.stockId(), detail.title(),
                detail.body(), detail.sentiment(), detail.tradingDay(), detail.createdAt(),
                detail.publishedAt(), detail.comments().size(),
                detail.comments().stream().map(CommentResponse::from).toList(),
                NewsDisclosureResponse.from(detail.disclosure()));
    }
}
