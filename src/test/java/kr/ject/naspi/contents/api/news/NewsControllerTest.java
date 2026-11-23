package kr.ject.naspi.contents.api.news;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.contents.application.NewsItem;
import kr.ject.naspi.contents.application.NewsPage;
import kr.ject.naspi.contents.application.NewsQueryService;
import kr.ject.naspi.contents.application.NewsRegenerationService;
import kr.ject.naspi.contents.application.RegeneratedNews;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.NewsStatus;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ActiveProfiles("test")
@WebMvcTest(NewsController.class)
@Import(SecurityConfig.class)
class NewsControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URL = "/api/leagues/3/stocks/11/news";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final LocalDateTime PUBLISHED_AT = CREATED_AT.plusMinutes(1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsQueryService newsQueryService;

    @MockitoBean
    private NewsRegenerationService newsRegenerationService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("커서와 개수를 생략하면 첫 페이지를 기본 20개로 조회하고 뉴스 목록과 다음 커서를 준다")
    void defaultPage() throws Exception {
        given(newsQueryService.findByStock(MEMBER_ID, 3L, 11L, null, 20))
                .willReturn(new NewsPage(List.of(item()), 30L));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.nextCursor").value(30))
                .andExpect(jsonPath("$.data.items[0].newsId").value(30))
                .andExpect(jsonPath("$.data.items[0].sentiment").value("POSITIVE"));
    }

    @Test
    @DisplayName("개수가 1~50을 벗어나면 HTTP 400이다")
    void invalidSize() throws Exception {
        mockMvc.perform(get(URL).param("size", "0").with(loginMember()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("size", "51").with(loginMember()))
                .andExpect(status().isBadRequest());

        then(newsQueryService).should(never()).findByStock(anyLong(), anyLong(), anyLong(),
                any(), anyInt());
    }

    @Test
    @DisplayName("뉴스 상세는 제목, 본문, 호재/악재, 발행 정보, 연결된 공시 ID를 준다")
    void detail() throws Exception {
        given(newsQueryService.getDetail(MEMBER_ID, 3L, 11L, 30L)).willReturn(item());

        mockMvc.perform(get(URL + "/30").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsId").value(30))
                .andExpect(jsonPath("$.data.stockId").value(11))
                .andExpect(jsonPath("$.data.disclosureId").value(20))
                .andExpect(jsonPath("$.data.title").value("뉴스 제목"))
                .andExpect(jsonPath("$.data.body").value("뉴스 본문"))
                .andExpect(jsonPath("$.data.sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.data.tradingDay").value(2))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.publishedAt").exists());
    }

    @Test
    @DisplayName("뉴스가 없거나 발행되지 않았으면 610을 돌려준다")
    void detailNotFound() throws Exception {
        given(newsQueryService.getDetail(MEMBER_ID, 3L, 11L, 99L))
                .willThrow(new NaspiBizException(ErrorCode.NEWS_NOT_FOUND));

        mockMvc.perform(get(URL + "/99").with(loginMember()))
                .andExpect(jsonPath("$.code").value("610"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void unauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/30")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("뉴스 재생성을 요청하면 뉴스 ID와 생성 중 상태를 준다")
    void regenerate() throws Exception {
        given(newsRegenerationService.regenerate(MEMBER_ID, 3L, 11L, 20L))
                .willReturn(new RegeneratedNews(30L, NewsStatus.PENDING));

        mockMvc.perform(post(URL.replace("/news", "") + "/disclosures/20/news/regenerate")
                        .with(loginMember()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsId").value(30))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("로그인하지 않으면 뉴스를 다시 생성할 수 없다")
    void regenerateUnauthorized() throws Exception {
        mockMvc.perform(post("/api/leagues/3/stocks/11/disclosures/20/news/regenerate")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private static NewsItem item() {
        return new NewsItem(30L, 11L, 20L, "뉴스 제목", "뉴스 본문", NewsSentiment.POSITIVE, 2,
                CREATED_AT, PUBLISHED_AT);
    }

    private static RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
