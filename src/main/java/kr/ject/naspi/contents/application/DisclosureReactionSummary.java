package kr.ject.naspi.contents.application;

import kr.ject.naspi.contents.domain.DisclosureReactionType;

/**
 * 공시 반응 집계와 요청한 플레이어의 현재 반응.
 *
 * @param disclosureId 공시 ID
 * @param likeCount 호재 반응 수
 * @param dislikeCount 악재 반응 수
 * @param myReaction 요청한 플레이어의 반응 (반응하지 않았거나 취소했으면 null)
 */
public record DisclosureReactionSummary(long disclosureId, long likeCount, long dislikeCount,
                                        DisclosureReactionType myReaction) {
}
