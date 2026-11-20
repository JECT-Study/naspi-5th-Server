package kr.ject.naspi.market.application;

/**
 * 리그 참여자에게 보여 주는 종목 이름과 소개.
 *
 * @param stockId 종목 ID
 * @param name 종목 이름
 * @param description 종목 소개
 * @param listerName 상장자 이름. 시작된 리그에서 탈퇴한 상장자면 '탈퇴한 사용자'
 * @param mine 조회한 회원 본인이 상장한 종목인지 여부
 */
public record MemberStock(long stockId, String name, String description, String listerName,
        boolean mine) {
}
