package kr.ject.naspi.contents.api.disclosure;

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
import kr.ject.naspi.contents.application.DisclosureDetail;
import kr.ject.naspi.contents.application.DisclosureListItem;
import kr.ject.naspi.contents.application.DisclosureNews;
import kr.ject.naspi.contents.application.DisclosurePage;
import kr.ject.naspi.contents.application.DisclosureQueryService;
import kr.ject.naspi.contents.application.DisclosureReactionSummary;
import kr.ject.naspi.contents.application.ImageUrl;
import kr.ject.naspi.contents.domain.DisclosureReactionType;
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
@WebMvcTest(DisclosureLookupController.class)
@Import(SecurityConfig.class)
class DisclosureLookupControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URL = "/api/leagues/3/stocks/11/disclosures";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final LocalDateTime EXPIRES_AT = CREATED_AT.plusMinutes(5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DisclosureQueryService disclosureQueryService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("종목별 공시 목록을 커서·개수로 조회하면 공시 목록과 다음 커서를 돌려준다")
    void findByStock() throws Exception {
        given(disclosureQueryService.findByStock(MEMBER_ID, 3L, 11L, "prev", 2))
                .willReturn(new DisclosurePage(List.of(new DisclosureListItem(30L, 11L, "제목",
                        "본문", 2, CREATED_AT, 70L, false,
                        List.of(new ImageUrl(100L, "https://signed/a", EXPIRES_AT),
                                new ImageUrl(101L, "https://signed/b", EXPIRES_AT)),
                        new DisclosureReactionSummary(30L, 4, 1,
                                DisclosureReactionType.LIKE),
                        new CommentPreview(3, new CommentItem(9L, "댓글", 71L, "상대", false,
                                CREATED_AT)))), "next"));

        mockMvc.perform(get(URL).param("cursor", "prev").param("size", "2").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.nextCursor").value("next"))
                .andExpect(jsonPath("$.data.items[0].type").value("DISCLOSURE"))
                .andExpect(jsonPath("$.data.items[0].disclosureId").value(30))
                .andExpect(jsonPath("$.data.items[0].title").value("제목"))
                .andExpect(jsonPath("$.data.items[0].tradingDay").value(2))
                .andExpect(jsonPath("$.data.items[0].mine").value(false))
                .andExpect(jsonPath("$.data.items[0].images.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].images[0].imageId").value(100))
                .andExpect(jsonPath("$.data.items[0].images[0].signedUrl")
                        .value("https://signed/a"))
                .andExpect(jsonPath("$.data.items[0].images[1].signedUrl")
                        .value("https://signed/b"))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(4))
                .andExpect(jsonPath("$.data.items[0].dislikeCount").value(1))
                .andExpect(jsonPath("$.data.items[0].myReaction").value("LIKE"))
                .andExpect(jsonPath("$.data.items[0].commentCount").value(3))
                .andExpect(jsonPath("$.data.items[0].previewComment.content").value("댓글"))
                .andExpect(jsonPath("$.data.items[0].previewComment.authorName").value("상대"))
                .andExpect(jsonPath("$.data.items[0].news").doesNotExist());
    }

    @Test
    @DisplayName("커서와 개수를 생략하면 첫 페이지를 기본 20개로 조회한다")
    void defaultPage() throws Exception {
        given(disclosureQueryService.findByStock(MEMBER_ID, 3L, 11L, null, 20))
                .willReturn(new DisclosurePage(List.of(), null));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("한 페이지 개수가 1~50을 벗어나면 HTTP 400으로 거절한다")
    void invalidSize() throws Exception {
        mockMvc.perform(get(URL).param("size", "0").with(loginMember()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("size", "51").with(loginMember()))
                .andExpect(status().isBadRequest());

        then(disclosureQueryService).should(never())
                .findByStock(anyLong(), anyLong(), anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("공시 상세는 목록 항목의 필드(이미지, 작성 정보, 반응, 댓글 수)에 댓글 전체와 AI 뉴스를 더해 돌려준다")
    void getDetail() throws Exception {
        List<ImageUrl> images = List.of(new ImageUrl(100L, "https://signed/a", EXPIRES_AT),
                new ImageUrl(101L, "https://signed/b", EXPIRES_AT));
        given(disclosureQueryService.getDetail(MEMBER_ID, 3L, 11L, 30L))
                .willReturn(new DisclosureDetail(30L, 11L, "제목", "본문", 2, CREATED_AT, 70L,
                        true, images, new DisclosureReactionSummary(30L, 0, 2, null),
                        List.of(new CommentItem(9L, "댓글", 71L, "상대", false, CREATED_AT)),
                        new DisclosureNews(40L, NewsStatus.PUBLISHED, "뉴스 제목", "뉴스 본문",
                                NewsSentiment.NEGATIVE)));

        mockMvc.perform(get(URL + "/30").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mine").value(true))
                .andExpect(jsonPath("$.data.images[1].imageId").value(101))
                .andExpect(jsonPath("$.data.images[1].signedUrl").value("https://signed/b"))
                .andExpect(jsonPath("$.data.dislikeCount").value(2))
                .andExpect(jsonPath("$.data.myReaction").doesNotExist())
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.previewComment").doesNotExist())
                .andExpect(jsonPath("$.data.comments[0].content").value("댓글"))
                .andExpect(jsonPath("$.data.comments.length()").value(1))
                .andExpect(jsonPath("$.data.news.newsId").value(40))
                .andExpect(jsonPath("$.data.news.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.news.title").value("뉴스 제목"))
                .andExpect(jsonPath("$.data.news.sentiment").value("NEGATIVE"));
    }

    @Test
    @DisplayName("삭제된 공시를 상세 조회하면 비즈니스 오류 코드로 응답한다")
    void deleted() throws Exception {
        given(disclosureQueryService.getDetail(anyLong(), anyLong(), anyLong(), anyLong()))
                .willThrow(new NaspiBizException(ErrorCode.DISCLOSURE_NOT_FOUND));

        mockMvc.perform(get(URL + "/30").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("607"));
    }

    @Test
    @DisplayName("로그인하지 않으면 공시를 조회할 수 없다")
    void unauthenticated() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/30")).andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
