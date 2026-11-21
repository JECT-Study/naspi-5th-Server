package kr.ject.naspi.member.application;

/**
 * 회원 탈퇴 이벤트. 탈퇴가 커밋된 뒤 카카오 연결을 끊는 데 쓴다.
 *
 * <p>회원 정보를 hard delete 하므로 카카오 회원 번호를 이벤트에 담아 둔다.
 *
 * @param memberId 탈퇴한 회원 ID
 * @param kakaoId 카카오 회원 번호
 */
public record MemberWithdrawn(long memberId, long kakaoId) {
}
