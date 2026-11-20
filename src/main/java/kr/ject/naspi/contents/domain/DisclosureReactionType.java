package kr.ject.naspi.contents.domain;

/**
 * 공시 반응 구분 (ERD: disclosure_reaction.반응구분 — 좋아요/싫어요).
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "반응"): 공시에 호재 또는 악재 버튼으로 반응한다. 이름은 ERD
 * 표기를 따르며, 좋아요가 호재, 싫어요가 악재다.
 */
public enum DisclosureReactionType {

    /** 좋아요 (호재). */
    LIKE,

    /** 싫어요 (악재). */
    DISLIKE
}
