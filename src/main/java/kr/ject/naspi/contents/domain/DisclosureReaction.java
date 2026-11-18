package kr.ject.naspi.contents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공시 반응 (ERD: disclosure_reaction). 참여자가 공시에 남긴 호재·악재 반응이다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "반응"): 호재와 악재 중 하나만 등록할 수 있다. 그래서 공시 1건에
 * 플레이어 1명당 반응 행은 하나뿐이다 (공시 ID + 플레이어 ID 유니크). 반응은 바꾸거나 취소할 수 있다
 * (docs/issues/v1/contents.md CNT-04 결정).
 */
@Entity
@Table(name = "disclosure_reaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_disclosure_reaction_player",
                columnNames = {"disclosure_id", "player_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    @Column(name = "disclosure_id", nullable = false, updatable = false)
    private Long disclosureId;

    @Column(name = "player_id", nullable = false, updatable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private DisclosureReactionType type;

    private DisclosureReaction(long leagueId, long disclosureId, long playerId,
                               DisclosureReactionType type) {
        this.leagueId = leagueId;
        this.disclosureId = disclosureId;
        this.playerId = playerId;
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * 공시에 반응을 남긴다.
     *
     * @param leagueId 리그 ID
     * @param disclosureId 공시 ID
     * @param playerId 반응하는 플레이어 ID
     * @param type 호재 또는 악재
     * @return 반응 (저장 전)
     */
    public static DisclosureReaction of(long leagueId, long disclosureId, long playerId,
                                        DisclosureReactionType type) {
        return new DisclosureReaction(leagueId, disclosureId, playerId, type);
    }

    /**
     * 반응을 호재↔악재로 바꾼다. 1인 1반응이므로 새 행을 만들지 않고 기존 반응을 바꾼다.
     *
     * @param type 바꿀 반응
     */
    public void changeTo(DisclosureReactionType type) {
        this.type = Objects.requireNonNull(type, "type");
    }
}
