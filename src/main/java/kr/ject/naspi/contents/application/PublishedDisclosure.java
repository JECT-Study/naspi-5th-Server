package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 작성된 공시.
 *
 * @param disclosureId 공시 ID
 * @param stockId 종목 ID
 * @param title 제목
 * @param body 본문
 * @param tradingDay 작성한 거래일 번호 (1~7)
 * @param createdAt 작성 일시 (KST)
 * @param imageIds 첨부 이미지 ID (첨부 순서대로, 없으면 빈 목록). 서명 URL은 공시 조회에서 발급한다.
 */
public record PublishedDisclosure(long disclosureId, long stockId, String title, String body,
                                  int tradingDay, LocalDateTime createdAt,
                                  List<Long> imageIds) {
}
