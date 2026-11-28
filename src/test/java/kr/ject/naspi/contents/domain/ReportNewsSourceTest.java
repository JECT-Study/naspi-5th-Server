package kr.ject.naspi.contents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportNewsSourceTest {

    private static List<String> images(int count) {
        return Collections.nCopies(count, "https://img.test/1.png");
    }

    @Test
    @DisplayName("제보 이미지는 최대 4개까지 입력할 수 있다")
    void maxImages() {
        assertThat(new ReportNewsSource("김철수", "이영희", "본문", images(4)).imageUrls())
                .hasSize(4);
        assertThat(new ReportNewsSource("김철수", "이영희", "본문", null).imageUrls()).isEmpty();
        assertThatThrownBy(() -> new ReportNewsSource("김철수", "이영희", "본문", images(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("종목 이름과 제보자 이름이 없으면 제보 뉴스를 생성할 수 없다")
    void requiredNames() {
        assertThatThrownBy(() -> new ReportNewsSource(null, "이영희", "본문", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportNewsSource(" ", "이영희", "본문", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportNewsSource("김철수", null, "본문", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportNewsSource("김철수", "", "본문", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("제보 본문은 필수다")
    void requiredBody() {
        assertThatThrownBy(() -> new ReportNewsSource("김철수", "이영희", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportNewsSource("김철수", "이영희", "  ", images(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
