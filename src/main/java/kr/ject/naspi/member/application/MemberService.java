package kr.ject.naspi.member.application;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.domain.Member;
import kr.ject.naspi.member.domain.MemberName;
import kr.ject.naspi.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 정보 조회·수정.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

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
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다."));
        member.changeName(name);
        return member.getName().getValue();
    }
}
