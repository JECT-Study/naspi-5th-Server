package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공시 상세. 목록 항목({@link DisclosureListItem})과 같은 필드에서 가장 먼저 등록된 댓글 대신 댓글 전체를 주고,
 * 연결된 뉴스가 더해진다.
 *
 * @param disclosureId 공시 ID
 * @param stockId 종목 ID
 * @param title 제목
 * @param body 본문
 * @param tradingDay 작성한 거래일 번호 (1~7)
 * @param createdAt 작성 일시
 * @param authorPlayerId 작성자(상장자) 플레이어 ID
 * @param mine 조회한 플레이어가 작성자인지 (삭제 버튼 표시용)
 * @param images 첨부 이미지의 서명 URL (첨부 순서)
 * @param reaction 반응 집계와 내 반응
 * @param comments 댓글 전체 (오래된 순). 댓글이 없으면 빈 목록
 * @param news 공시로 만든 AI 뉴스. 뉴스 행이 없으면 null
 */
public record DisclosureDetail(long disclosureId, long stockId, String title, String body,
                               int tradingDay, LocalDateTime createdAt, long authorPlayerId,
                               boolean mine, List<ImageUrl> images,
                               DisclosureReactionSummary reaction, List<CommentItem> comments,
                               DisclosureNews news) {
}
