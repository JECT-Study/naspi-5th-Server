package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;

/**
 * 공시 목록의 공시 1건.
 *
 * @param disclosureId 공시 ID
 * @param stockId 종목 ID
 * @param title 제목
 * @param body 본문
 * @param tradingDay 작성한 거래일 번호 (1~7)
 * @param createdAt 작성 일시
 * @param authorPlayerId 작성자(상장자) 플레이어 ID
 * @param mine 조회한 플레이어가 작성자인지 (삭제 버튼 표시용)
 * @param imageCount 첨부 이미지 수
 * @param thumbnail 첫 번째 이미지의 서명 URL. 이미지가 없으면 null
 * @param reaction 반응 집계와 내 반응
 */
public record DisclosureListItem(long disclosureId, long stockId, String title, String body,
                                 int tradingDay, LocalDateTime createdAt, long authorPlayerId,
                                 boolean mine, int imageCount, ImageUrl thumbnail,
                                 DisclosureReactionSummary reaction) {
}
