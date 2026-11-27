package kr.ject.naspi.contents.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureReaction;
import kr.ject.naspi.contents.domain.DisclosureReactionRepository;
import kr.ject.naspi.contents.domain.DisclosureReactionType;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공시 반응 (호재·악재) 토글. 한 번 누르면 등록, 같은 버튼을 다시 누르면 취소, 다른 버튼을 누르면 변경한다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "반응"): 참여자는 공시에 호재 또는 악재로 반응할 수 있고, 두 반응
 * 중 하나만 등록할 수 있다. 반응은 AI 뉴스 생성과 내용에 영향을 주지 않으므로 이벤트를 발행하지 않는다.
 *
 * <p>결정 (docs/issues/v1/contents.md CNT-04): 반응은 바꾸거나 취소할 수 있고, 본인 공시에도 반응할 수 있다.
 * 진행 중인 리그에서만 반응할 수 있다.
 */
@Service
public class DisclosureReactionService {

    private final DisclosureRepository disclosureRepository;
    private final DisclosureReactionRepository reactionRepository;
    private final LeagueQueryService leagueQueryService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 공시 반응 서비스를 만든다.
     *
     * @param disclosureRepository 공시 저장소
     * @param reactionRepository 공시 반응 저장소
     * @param leagueQueryService 리그 상태·플레이어 조회
     * @param transactionManager 반응 저장 트랜잭션
     */
    public DisclosureReactionService(DisclosureRepository disclosureRepository,
                                     DisclosureReactionRepository reactionRepository,
                                     LeagueQueryService leagueQueryService,
                                     PlatformTransactionManager transactionManager) {
        this.disclosureRepository = disclosureRepository;
        this.reactionRepository = reactionRepository;
        this.leagueQueryService = leagueQueryService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 누른 반응 버튼을 토글한다. 프론트는 현재 반응을 몰라도 누른 버튼만 보내면 된다.
     *
     * <ul>
     *   <li>반응이 없으면 누른 반응을 등록한다.</li>
     *   <li>같은 반응을 다시 누르면 취소한다.</li>
     *   <li>다른 반응을 누르면 그 반응으로 바꾼다 (1인 1반응).</li>
     * </ul>
     *
     * @param memberId 반응하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @param type 누른 반응 (호재 또는 악재)
     * @return 토글 후 집계와 내 반응 (취소됐으면 내 반응은 null)
     * @throws NaspiBizException 리그에 입장하지 않았거나, 리그가 진행 중이 아니거나, 공시가 없는 경우
     */
    public DisclosureReactionSummary toggle(long memberId, long leagueId, long stockId,
                                            long disclosureId, DisclosureReactionType type) {
        long playerId = reactablePlayerId(memberId, leagueId);
        return retryOnConflict(() -> transactionTemplate.execute(status -> {
            findReactableDisclosure(leagueId, stockId, disclosureId);
            Optional<DisclosureReaction> existing =
                    reactionRepository.findByDisclosureIdAndPlayerId(disclosureId, playerId);
            if (existing.isEmpty()) {
                // 처음 누름: 반응 등록
                reactionRepository.saveAndFlush(
                        DisclosureReaction.of(leagueId, disclosureId, playerId, type));
                return summarize(disclosureId, type);
            }
            DisclosureReaction reaction = existing.get();
            if (reaction.getType() == type) {
                // 같은 버튼을 다시 누름: 반응 취소
                reactionRepository.delete(reaction);
                return summarize(disclosureId, null);
            }
            // 다른 버튼을 누름: 1인 1반응이므로 새 행 없이 호재↔악재로 바꾼다
            reaction.changeTo(type);
            return summarize(disclosureId, type);
        }));
    }

    /**
     * 여러 공시의 반응을 한 번에 집계한다 (공시 목록·상세 조회용). 반응이 없는 공시도 0으로 담는다.
     *
     * @param disclosureIds 공시 ID 목록
     * @param playerId 조회하는 플레이어 ID (내 반응 표시용)
     * @return 공시 ID별 반응 집계
     */
    public Map<Long, DisclosureReactionSummary> summarizeAll(Collection<Long> disclosureIds,
                                                             long playerId) {
        if (disclosureIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<DisclosureReaction>> byDisclosure = reactionRepository
                .findAllByDisclosureIdIn(disclosureIds).stream()
                .collect(Collectors.groupingBy(DisclosureReaction::getDisclosureId));
        Map<Long, DisclosureReactionSummary> summaries = new HashMap<>();
        for (long disclosureId : disclosureIds) {
            List<DisclosureReaction> reactions = byDisclosure.getOrDefault(disclosureId,
                    List.of());
            summaries.put(disclosureId, new DisclosureReactionSummary(disclosureId,
                    countOf(reactions, DisclosureReactionType.LIKE),
                    countOf(reactions, DisclosureReactionType.DISLIKE),
                    reactions.stream()
                            .filter(r -> r.getPlayerId() == playerId)
                            .map(DisclosureReaction::getType)
                            .findFirst()
                            .orElse(null)));
        }
        return summaries;
    }

    private static long countOf(List<DisclosureReaction> reactions,
                                DisclosureReactionType type) {
        return reactions.stream().filter(r -> r.getType() == type).count();
    }

    /** 리그에 입장한 회원인지, 리그가 진행 중인지 확인하고 플레이어 ID를 돌려준다. */
    private long reactablePlayerId(long memberId, long leagueId) {
        // 반응은 거래 판단용 신호라 거래가 열린 진행 중 리그에서만 받는다 (리그가 없으면(204) 여기서 오류)
        boolean started = leagueQueryService.findCurrentTradingDay(leagueId).isPresent();
        // 반응은 플레이어 단위로 1개이므로 회원을 이 리그의 플레이어로 바꾼다
        // (입장하지 않았거나(004) 종료 시각이 지난 리그면(212) 여기서 오류)
        long playerId = leagueQueryService.requireAccessiblePlayer(memberId, leagueId);
        // 남은 경우는 시작 전(대기실) 리그
        if (!started) {
            throw new NaspiBizException(ErrorCode.DISCLOSURE_REACTION_LEAGUE_NOT_STARTED);
        }
        return playerId;
    }

    /** 경로의 리그·종목에 속하고 삭제되지 않은 공시만 반응 대상으로 본다. */
    private Disclosure findReactableDisclosure(long leagueId, long stockId, long disclosureId) {
        return disclosureRepository.findById(disclosureId)
                .filter(d -> d.getLeagueId() == leagueId && d.getStockId() == stockId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new NaspiBizException(ErrorCode.DISCLOSURE_NOT_FOUND));
    }

    /** 공시의 반응 요약을 계산한다. */
    private DisclosureReactionSummary summarize(long disclosureId,
                                                DisclosureReactionType myReaction) {
        return new DisclosureReactionSummary(disclosureId,
                reactionRepository.countByDisclosureIdAndType(disclosureId,
                        DisclosureReactionType.LIKE),
                reactionRepository.countByDisclosureIdAndType(disclosureId,
                        DisclosureReactionType.DISLIKE),
                myReaction);
    }

    /**
     * 같은 플레이어의 첫 반응이 동시에 두 번 들어오면(버튼 연타) 한쪽이 유니크 제약에 걸린다. 그때는 먼저 저장된
     * 반응이 있으므로 한 번 더 실행해 토글로 처리한다 (두 번 누른 것과 같은 결과).
     */
    private static <T> T retryOnConflict(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException e) {
            return action.get();
        }
    }
}
