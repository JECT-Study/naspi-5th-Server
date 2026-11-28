package kr.ject.naspi.contents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.contents.domain.ReportNewsGenerationResult.Generated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportNewsGenerationResultTest {

    private static final ReportNewsSource SOURCE =
            new ReportNewsSource("김철수", "이영희", "본문", null);
    private static final String BODY = "점심 장소를 바꿨다. 제보자 이영희";

    private static Generated of(String title, String body) {
        return Generated.of(SOURCE, title, body, NewsSentiment.POSITIVE);
    }

    @Test
    @DisplayName("제보 뉴스 제목은 최대 30자, 본문은 최대 100자다")
    void maxLength() {
        String titlePrefix = "김철수, ";
        String bodySuffix = "제보자 이영희";
        assertThatCode(() -> of(titlePrefix + "가".repeat(30 - titlePrefix.length()), BODY))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> of(titlePrefix + "가".repeat(31 - titlePrefix.length()), BODY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> of("김철수, 제목", "가".repeat(100 - bodySuffix.length()) + bodySuffix))
                .doesNotThrowAnyException();
        assertThatThrownBy(() ->
                of("김철수, 제목", "가".repeat(101 - bodySuffix.length()) + bodySuffix))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("제보 뉴스 제목은 `{종목 이름}, `(쉼표 뒤 공백 한 칸)으로 시작하고 그 뒤에 내용이 있어야 한다")
    void titleStartsWithStockName() {
        assertThatCode(() -> of("김철수, 점심 변경", BODY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> of("점심 변경", BODY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수 점심 변경", BODY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수,점심 변경", BODY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("박민수, 점심 변경", BODY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수, ", BODY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수,   ", BODY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("종목 이름에 쉼표가 있어도 제목은 종목 이름 + 쉼표 + 공백으로 시작하면 통과한다")
    void stockNameWithComma() {
        ReportNewsSource source = new ReportNewsSource("a,b", "이영희", "본문", null);

        assertThatCode(() -> Generated.of(source, "a,b, 내용", BODY, NewsSentiment.NEGATIVE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("제보 뉴스 본문은 `제보자 {제보자 이름}`으로 끝나야 한다")
    void bodyCitesReporter() {
        assertThatCode(() -> of("김철수, 제목", "제보자 이영희")).doesNotThrowAnyException();
        assertThatThrownBy(() -> of("김철수, 제목", "제보자 이영희가 알렸다. 점심 변경"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수, 제목", "점심 변경. 제보자 이영희."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수, 제목", "점심 변경. 제보자 이영희 "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수, 제목", "점심 변경. 이영희"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수, 제목", "점심 변경. 제보자 박민수"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수, 제목", "점심 변경. 제보 이영희"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("제목·본문·호재·악재가 없으면 생성 결과로 인정하지 않는다")
    void requiredFields() {
        assertThatThrownBy(() -> of(" ", BODY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of(null, BODY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수, 제목", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("김철수, 제목", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Generated.of(SOURCE, "김철수, 제목", BODY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("제보 뉴스 미생성 사유는 '제보 내용 부족'이다")
    void skippedReason() {
        assertThat(new ReportNewsGenerationResult.Skipped().reason()).isEqualTo("제보 내용 부족");
    }
}
