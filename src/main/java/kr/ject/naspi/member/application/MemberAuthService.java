package kr.ject.naspi.member.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.domain.Member;
import kr.ject.naspi.member.domain.MemberName;
import kr.ject.naspi.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오 계정 기반 회원 로그인·가입.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAuthService {

    private final MemberRepository memberRepository;
    private final Clock clock;

    /**
     * 카카오 계정으로 가입한 회원을 찾는다.
     *
     * @param kakaoId 카카오 회원 번호
     * @return 회원 ID, 가입하지 않았으면 빈 값
     */
    public Optional<Long> findMemberId(long kakaoId) {
        return memberRepository.findByKakaoId(kakaoId).map(Member::getId);
    }

    /**
     * 카카오 이름으로 자동 가입한다.
     *
     * <p>카카오 이름을 서비스 이름 규칙에 맞게 변환해 쓴다. 카카오 이름에 한글이 없으면 가입하지 않고 빈 값을 반환하며,
     * 이 경우 사용자가 이름을 입력해 {@link #joinWithInputName}으로 가입한다.
     *
     * @param kakaoId 카카오 회원 번호
     * @param kakaoNickname 카카오 닉네임, 없으면 null
     * @return 가입한 회원 ID, 이름 입력이 필요하면 빈 값
     * @throws NaspiBizException 이미 가입한 경우
     */
    @Transactional
    public Optional<Long> joinWithKakaoName(long kakaoId, String kakaoNickname) {
        return MemberName.fromKakaoName(kakaoNickname).map(name -> join(kakaoId, name));
    }

    /**
     * 사용자가 입력한 이름으로 가입한다. 카카오 이름에 한글이 없는 사용자가 쓴다.
     *
     * @param kakaoId 카카오 회원 번호
     * @param inputName 사용자가 입력한 이름
     * @return 가입한 회원 ID
     * @throws NaspiBizException 이미 가입했거나, 이름이 규칙(최대 5자 한글)에 맞지 않는 경우
     */
    @Transactional
    public long joinWithInputName(long kakaoId, String inputName) {
        return join(kakaoId, MemberName.of(inputName));
    }

    private long join(long kakaoId, MemberName name) {
        if (memberRepository.existsByKakaoId(kakaoId)) {
            throw new NaspiBizException(ErrorCode.MEMBER_ALREADY_JOINED);
        }
        Member member = Member.join(kakaoId, name, LocalDateTime.now(clock));
        return memberRepository.save(member).getId();
    }
}
