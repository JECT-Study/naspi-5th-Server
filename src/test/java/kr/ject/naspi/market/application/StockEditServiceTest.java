package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.domain.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import(StockEditService.class)
class StockEditServiceTest {

    private static final long LEAGUE_ID = 1L;
    private static final long OWNER = 10L;

    @Autowired
    private StockEditService service;

    @Autowired
    private StockRepository stockRepository;

    private long stockId;

    @BeforeEach
    void setUp() {
        stockId = save(LEAGUE_ID, OWNER, "나스피", "원래 소개").getId();
    }

    @Test
    @DisplayName("상장자는 대기실에서 종목 이름과 소개를 수정할 수 있다")
    void editInLobby() {
        StockDetail detail = service.editInLobby(LEAGUE_ID, OWNER, stockId, " 새이름 ", "새 소개");

        assertThat(detail).isEqualTo(new StockDetail(stockId, "새이름", "새 소개"));
        Stock stock = stockRepository.findById(stockId).orElseThrow();
        assertThat(stock.getName().getValue()).isEqualTo("새이름");
        assertThat(stock.getDescription().getValue()).isEqualTo("새 소개");
    }

    @Test
    @DisplayName("현재 이름 그대로 두고 소개만 바꿔 수정해도 이름 중복으로 막히지 않는다")
    void editInLobbyKeepingName() {
        StockDetail detail = service.editInLobby(LEAGUE_ID, OWNER, stockId, "나스피", "새 소개");

        assertThat(detail.name()).isEqualTo("나스피");
        assertThat(detail.description()).isEqualTo("새 소개");
    }

    @Test
    @DisplayName("같은 리그의 다른 종목과 이름이 같게 수정할 수 없다")
    void editInLobbyWithDuplicatedName() {
        save(LEAGUE_ID, 11L, "다른종목", "소개");

        assertThatThrownBy(() -> service.editInLobby(LEAGUE_ID, OWNER, stockId, "다른종목", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("다른 리그의 종목과는 이름이 같아도 수정할 수 있다")
    void editInLobbyWithSameNameInOtherLeague() {
        save(2L, 20L, "다른종목", "소개");

        StockDetail detail = service.editInLobby(LEAGUE_ID, OWNER, stockId, "다른종목", "소개");

        assertThat(detail.name()).isEqualTo("다른종목");
    }

    @Test
    @DisplayName("대기실 수정에도 상장과 같은 이름·소개 규칙이 적용되고, 어긋나면 수정되지 않는다")
    void editInLobbyWithInvalidInput() {
        assertThatThrownBy(() -> service.editInLobby(LEAGUE_ID, OWNER, stockId, "새이름!", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_NAME);
        assertThatThrownBy(() -> service.editInLobby(LEAGUE_ID, OWNER, stockId, "새이름", " "))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_DESCRIPTION);

        Stock stock = stockRepository.findById(stockId).orElseThrow();
        assertThat(stock.getName().getValue()).isEqualTo("나스피");
        assertThat(stock.getDescription().getValue()).isEqualTo("원래 소개");
    }

    @Test
    @DisplayName("상장자는 종목 소개만 수정할 수 있고 이름은 그대로다")
    void changeDescription() {
        StockDetail detail = service.changeDescription(LEAGUE_ID, OWNER, stockId, "새 소개");

        assertThat(detail).isEqualTo(new StockDetail(stockId, "나스피", "새 소개"));
    }

    @Test
    @DisplayName("소개 수정에도 소개 규칙(1~50자)이 적용된다")
    void changeDescriptionWithInvalidDescription() {
        String tooLong = "가".repeat(51);

        assertThatThrownBy(() -> service.changeDescription(LEAGUE_ID, OWNER, stockId, tooLong))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_DESCRIPTION);
    }

    @Test
    @DisplayName("본인이 상장하지 않은 종목은 수정할 수 없다")
    void onlyOwnerCanEdit() {
        assertThatThrownBy(() -> service.editInLobby(LEAGUE_ID, 99L, stockId, "새이름", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_OWNED);
        assertThatThrownBy(() -> service.changeDescription(LEAGUE_ID, 99L, stockId, "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_OWNED);
    }

    @Test
    @DisplayName("없는 종목이거나 다른 리그의 종목이면 찾을 수 없다")
    void stockNotFound() {
        assertThatThrownBy(() -> service.changeDescription(LEAGUE_ID, OWNER, 9999L, "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_FOUND);
        assertThatThrownBy(() -> service.changeDescription(2L, OWNER, stockId, "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_FOUND);
    }

    private Stock save(long leagueId, long playerId, String name, String description) {
        return stockRepository.save(Stock.list(leagueId, playerId, StockName.of(name),
                StockDescription.of(description)));
    }
}
