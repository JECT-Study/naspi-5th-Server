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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 (ERD: member).
 *
 * <p>카카오 계정으로만 가입한다. 개인정보처리방침과 서비스이용약관은 카카오 로그인 동의 화면에서 필수로 동의받으므로
 * 카카오 인증을 마친 사용자는 약관에 동의한 것으로 본다.
 *
 * <p>탈퇴하면 회원 정보를 즉시 삭제(hard delete)하므로 탈퇴 여부를 따로 저장하지 않는다.
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

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    private Member(Long kakaoId, MemberName name, LocalDateTime joinedAt) {
        this.kakaoId = Objects.requireNonNull(kakaoId, "kakaoId");
        this.name = Objects.requireNonNull(name, "name");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
        this.termsAgreed = true;
    }

    /**
     * 카카오 계정으로 회원가입한다.
     *
     * <p>약관 동의는 카카오 동의 화면에서 받으므로 약관 동의 상태로 가입된다.
     *
     * @param kakaoId 카카오 회원 번호
     * @param name 회원 이름
     * @param joinedAt 가입 일시
     * @return 가입한 회원
     */
    public static Member join(Long kakaoId, MemberName name, LocalDateTime joinedAt) {
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
}
