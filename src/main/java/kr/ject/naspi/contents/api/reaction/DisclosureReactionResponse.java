package kr.ject.naspi.contents.api.reaction;

import kr.ject.naspi.contents.application.DisclosureReactionSummary;
import kr.ject.naspi.contents.domain.DisclosureReactionType;

/**
 * 공시 반응 응답. 토글 후의 집계와 내 반응을 돌려줘 화면을 바로 갱신할 수 있게 한다.
 *
 * @param disclosureId 공시 ID
 * @param likeCount 호재 반응 수
 * @param dislikeCount 악재 반응 수
 * @param myReaction 내 반응 ({@code LIKE}, {@code DISLIKE}, 반응 없으면 null)
 */
record DisclosureReactionResponse(long disclosureId, long likeCount, long dislikeCount,
                                  DisclosureReactionType myReaction) {

    static DisclosureReactionResponse from(DisclosureReactionSummary summary) {
        return new DisclosureReactionResponse(summary.disclosureId(), summary.likeCount(),
                summary.dislikeCount(), summary.myReaction());
    }
}
