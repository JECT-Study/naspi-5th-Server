package kr.ject.naspi.contents.api.reaction;

import jakarta.validation.constraints.NotNull;
import kr.ject.naspi.contents.domain.DisclosureReactionType;

/**
 * 공시 반응 토글 요청.
 *
 * @param type 누른 버튼. 호재({@code LIKE}) 또는 악재({@code DISLIKE})
 */
record ReactToDisclosureRequest(@NotNull DisclosureReactionType type) {
}
