package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 작성된 제보.
 *
 * @param reportId 제보 ID
 * @param stockId 대상 종목 ID
 * @param body 본문
 * @param tradingDay 작성한 거래일 번호 (1~7)
 * @param createdAt 작성 일시 (KST)
 * @param imageIds 첨부 이미지 ID (첨부 순서대로, 없으면 빈 목록)
 * @param newsId 생성 중인 제보 기반 AI 뉴스 ID. 발행되기 전에는 뉴스 조회에 나오지 않는다
 */
public record SubmittedReport(long reportId, long stockId, String body, int tradingDay,
                              LocalDateTime createdAt, List<Long> imageIds, long newsId) {
}
