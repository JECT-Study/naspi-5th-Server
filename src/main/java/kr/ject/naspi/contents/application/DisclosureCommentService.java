package kr.ject.naspi.contents.application;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.ject.naspi.contents.domain.CommentContent;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureComment;
import kr.ject.naspi.contents.domain.DisclosureCommentRepository;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import org.springframework.stereotype.Service;

/**
 * 공시 댓글 작성·조회·삭제. 절차는 {@link CommentService}를 따른다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "공시"): 삭제된 공시와 공시에 달린 댓글은 조회할 수 없으므로,
 * 삭제된 공시의 댓글은 작성·조회·삭제 모두 공시가 없는 것으로 거절한다.
 */
@Service
public class DisclosureCommentService extends CommentService<DisclosureComment, Disclosure> {

    private final DisclosureRepository disclosureRepository;

    /**
     * 공시 댓글 서비스를 만든다.
     *
     * @param commentRepository 댓글 저장소
     * @param disclosureRepository 공시 저장소
     * @param leagueQueryService 리그 상태·플레이어 조회
     * @param clock 작성 일시 기준 시계
     */
    public DisclosureCommentService(DisclosureCommentRepository commentRepository,
                                    DisclosureRepository disclosureRepository,
                                    LeagueQueryService leagueQueryService, Clock clock) {
        super(commentRepository, leagueQueryService, clock);
        this.disclosureRepository = disclosureRepository;
    }

    /** 경로의 리그·종목에 속하고 삭제되지 않은 공시만 댓글 대상으로 본다. */
    @Override
    protected Disclosure findTarget(long leagueId, long stockId, long disclosureId) {
        return disclosureRepository.findById(disclosureId)
                .filter(d -> d.getLeagueId() == leagueId && d.getStockId() == stockId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new NaspiBizException(ErrorCode.DISCLOSURE_NOT_FOUND));
    }

    @Override
    protected DisclosureComment newComment(Disclosure disclosure, long playerId,
                                           CommentContent content, LocalDateTime createdAt) {
        return DisclosureComment.write(disclosure, playerId, content, createdAt);
    }
}
