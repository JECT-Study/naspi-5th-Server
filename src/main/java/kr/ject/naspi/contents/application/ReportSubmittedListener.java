package kr.ject.naspi.contents.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 제보 작성 시 생성 중인 제보 기반 AI 뉴스의 내용을 채운다.
 *
 * <p>제보 저장이 커밋된 뒤 별도 스레드에서 실행되므로 제보 작성 응답을 늦추지 않고, 실패해도 제보는 남는다.
 */
@Component
@RequiredArgsConstructor
class ReportSubmittedListener {

    private final ReportNewsGenerationService reportNewsGenerationService;

    /**
     * 작성된 제보의 AI 뉴스를 생성한다.
     *
     * @param event 제보 작성 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportSubmitted(ReportSubmitted event) {
        reportNewsGenerationService.generate(event.reportId());
    }
}
