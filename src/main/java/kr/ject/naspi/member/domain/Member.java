package kr.ject.naspi.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import kr.ject.naspi.global.exception.BusinessException;
import kr.ject.naspi.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 (ERD: member).
 *
 * <p>카카오 계정으로만 가입하며, 개인정보처리방침과 서비스이용약관에 동의해야 가입할 수 있다.
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kakao_id", nullable = false, unique = true)
    private Long kakaoId;

    @Embedded
    private MemberName name;

    @Column(name = "terms_agreed", nullable = false)
    private boolean termsAgreed;

    @Column(name = "withdrawn", nullable = false)
    private boolean withdrawn;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    private Member(Long kakaoId, MemberName name, LocalDateTime joinedAt) {
        this.kakaoId = Objects.requireNonNull(kakaoId, "kakaoId");
        this.name = Objects.requireNonNull(name, "name");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
        this.termsAgreed = true;
        this.withdrawn = false;
    }

    /**
     * 카카오 계정으로 회원가입한다.
     *
     * @param kakaoId 카카오 회원 번호
     * @param name 회원 이름
     * @param termsAgreed 개인정보처리방침·서비스이용약관 동의 여부
     * @param joinedAt 가입 일시
     * @return 가입한 회원
     * @throws BusinessException 약관에 동의하지 않은 경우
     */
    public static Member join(
            Long kakaoId, MemberName name, boolean termsAgreed, LocalDateTime joinedAt) {
        if (!termsAgreed) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "약관에 동의해야 가입할 수 있습니다.");
        }
        return new Member(kakaoId, name, joinedAt);
    }

    /**
     * 이름을 변경한다.
     *
     * @param newName 새 이름
     */
    public void changeName(MemberName newName) {
        this.name = Objects.requireNonNull(newName, "newName");
    }

    /**
     * 탈퇴 처리한다.
     *
     * <p>ERD의 탈퇴여부(soft delete)를 따른다. v1 정책은 hard delete이므로 방식이 확정되면 변경될 수 있다.
     */
    public void withdraw() {
        this.withdrawn = true;
    }
}
