package kr.ject.naspi.contents.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 공시 게시·뉴스 재생성 요청 시 생성 중인 AI 뉴스의 내용을 채운다.
 *
 * <p>공시 저장이 커밋된 뒤 별도 스레드에서 실행되므로 공시 작성 응답을 늦추지 않고, 실패해도 공시는 남는다.
 */
@Component
@RequiredArgsConstructor
class DisclosurePublishedListener {

    private final NewsGenerationService newsGenerationService;

    /**
     * 게시된 공시의 AI 뉴스를 생성한다.
     *
     * @param event 공시 게시 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDisclosurePublished(DisclosurePublished event) {
        newsGenerationService.generate(event.disclosureId());
    }

    /**
     * 재생성을 요청한 뉴스를 다시 생성한다. 첫 생성과 같은 흐름이라 자동 재시도도 똑같이 적용된다.
     *
     * @param event 뉴스 재생성 요청 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewsRegenerationRequested(NewsRegenerationRequested event) {
        newsGenerationService.generate(event.disclosureId());
    }
}
