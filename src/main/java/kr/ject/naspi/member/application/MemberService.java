package kr.ject.naspi.member.application;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.domain.Member;
import kr.ject.naspi.member.domain.MemberName;
import kr.ject.naspi.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 정보 조회·수정, 탈퇴.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 회원 이름을 조회한다.
     *
     * @param memberId 회원 ID
     * @return 이름
     * @throws NaspiBizException 회원이 없는 경우
     */
    public String getName(long memberId) {
        return getMember(memberId).getName().getValue();
    }

    /**
     * 회원 이름을 수정한다. 회원가입 시 입력하는 이름과 같은 규칙(최대 5자 한글, 중복 허용)을 따른다.
     *
     * @param memberId 회원 ID
     * @param newName 새 이름
     * @return 수정된 이름
     * @throws NaspiBizException 회원이 없거나, 이름이 규칙에 맞지 않는 경우
     */
    @Transactional
    public String changeName(long memberId, String newName) {
        MemberName name = MemberName.of(newName);
        Member member = getMember(memberId);
        member.changeName(name);
        return member.getName().getValue();
    }

    /**
     * 회원을 즉시 탈퇴시킨다.
     *
     * <p>정책 (docs/policies/v1/member.md "회원 탈퇴"): 회원 정보를 hard delete 한다.
     * 다른 모듈이 후속 처리(주문 취소, 자산·종목·리그 결과 처리)를 하도록 삭제 전에 {@link MemberWithdrawn}을 발행한다.
     * 탈퇴 후 같은 카카오 계정으로 로그인하면 신규 회원으로 가입된다.
     *
     * @param memberId 회원 ID
     * @throws NaspiBizException 회원이 없는 경우
     */
    @Transactional
    public void withdraw(long memberId) {
        Member member = getMember(memberId);
        eventPublisher.publishEvent(new MemberWithdrawn(memberId));
        memberRepository.delete(member);
    }

    private Member getMember(long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
