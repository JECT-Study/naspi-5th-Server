package kr.ject.naspi.contents.api.feed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.contents.application.CommentItem;
import kr.ject.naspi.contents.application.CommentPreview;
import kr.ject.naspi.contents.application.DisclosureListItem;
import kr.ject.naspi.contents.application.DisclosureReactionSummary;
import kr.ject.naspi.contents.application.FeedItemType;
import kr.ject.naspi.contents.application.FeedPage;
import kr.ject.naspi.contents.application.FeedQueryService;
import kr.ject.naspi.contents.application.ImageUrl;
import kr.ject.naspi.contents.application.NewsListItem;
import kr.ject.naspi.contents.domain.DisclosureReactionType;
import kr.ject.naspi.contents.domain.NewsSentiment;
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
@WebMvcTest(FeedController.class)
@Import(SecurityConfig.class)
class FeedControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URL = "/api/leagues/3/feed";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 17, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedQueryService feedQueryService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("필터·커서·개수를 생략하면 리그 전체 첫 페이지를 기본 5개로 조회하고, 항목은 종목별 목록 항목에 type을 더한 모양이다")
    void defaultPage() throws Exception {
        given(feedQueryService.find(MEMBER_ID, 3L, null, null, null, 5))
                .willReturn(new FeedPage(List.of(news(), disclosure()), "next"));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.nextCursor").value("next"))
                .andExpect(jsonPath("$.data.items[0].type").value("NEWS"))
                .andExpect(jsonPath("$.data.items[0].newsId").value(30))
                .andExpect(jsonPath("$.data.items[0].sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.data.items[0].commentCount").value(3))
                .andExpect(jsonPath("$.data.items[0].previewComment.content").value("댓글"))
                .andExpect(jsonPath("$.data.items[0].disclosureId").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].type").value("DISCLOSURE"))
                .andExpect(jsonPath("$.data.items[1].disclosureId").value(20))
                .andExpect(jsonPath("$.data.items[1].images[0].signedUrl")
                        .value("https://signed/a"))
                .andExpect(jsonPath("$.data.items[1].likeCount").value(1))
                .andExpect(jsonPath("$.data.items[1].myReaction").value("LIKE"))
                .andExpect(jsonPath("$.data.items[1].commentCount").value(3))
                .andExpect(jsonPath("$.data.items[1].newsId").doesNotExist());
    }

    @Test
    @DisplayName("종목·종류·커서·개수를 그대로 넘겨 조회한다")
    void withParams() throws Exception {
        given(feedQueryService.find(MEMBER_ID, 3L, 11L, FeedItemType.DISCLOSURE, "abc", 10))
                .willReturn(new FeedPage(List.of(), null));

        mockMvc.perform(get(URL).param("stockId", "11").param("type", "DISCLOSURE")
                        .param("cursor", "abc").param("size", "10").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.nextCursor").isEmpty());
    }

    @Test
    @DisplayName("개수가 1~50을 벗어나거나 종류가 올바르지 않으면 HTTP 400이다")
    void invalidParams() throws Exception {
        mockMvc.perform(get(URL).param("size", "0").with(loginMember()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("size", "51").with(loginMember()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("type", "COMMENT").with(loginMember()))
                .andExpect(status().isBadRequest());

        then(feedQueryService).should(never()).find(anyLong(), anyLong(), any(), any(), any(),
                anyInt());
    }

    @Test
    @DisplayName("로그인하지 않으면 HTTP 401이다")
    void unauthenticated() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private static NewsListItem news() {
        return new NewsListItem(30L, 11L, "뉴스 제목", "뉴스 본문", NewsSentiment.POSITIVE, 2,
                CREATED_AT, CREATED_AT.plusMinutes(1), comments());
    }

    private static DisclosureListItem disclosure() {
        return new DisclosureListItem(20L, 11L, "공시 제목", "공시 본문", 2, CREATED_AT, 70L, false,
                List.of(new ImageUrl(1L, "https://signed/a", CREATED_AT.plusMinutes(5))),
                new DisclosureReactionSummary(20L, 1, 0, DisclosureReactionType.LIKE),
                comments());
    }

    private static CommentPreview comments() {
        return new CommentPreview(3, new CommentItem(1L, "댓글", 80L, "나", true, CREATED_AT));
    }

    private static RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
