package kr.ject.naspi.contents.api.comment;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import kr.ject.naspi.contents.application.CommentItem;
import kr.ject.naspi.contents.application.NewsCommentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ActiveProfiles("test")
@WebMvcTest(NewsCommentController.class)
@Import(SecurityConfig.class)
class NewsCommentControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URL = "/api/leagues/3/stocks/11/news/5/comments";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 17, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsCommentService commentService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("댓글을 작성하면 작성한 댓글을 돌려준다")
    void write() throws Exception {
        given(commentService.write(MEMBER_ID, 3L, 11L, 5L, "댓글"))
                .willReturn(item());

        mockMvc.perform(post(URL).with(loginMember()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.commentId").value(9))
                .andExpect(jsonPath("$.data.authorName").value("나"))
                .andExpect(jsonPath("$.data.mine").value(true));
    }

    @Test
    @DisplayName("댓글 내용이 누락되면 HTTP 400으로 거절한다")
    void missingContent() throws Exception {
        mockMvc.perform(post(URL).with(loginMember()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        then(commentService).should(never()).write(anyLong(), anyLong(), anyLong(), anyLong(),
                anyString());
    }

    @Test
    @DisplayName("댓글 목록은 페이징 없이 전체를 배열로 준다")
    void list() throws Exception {
        given(commentService.findByTarget(MEMBER_ID, 3L, 11L, 5L)).willReturn(List.of(item()));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("댓글"));
    }

    @Test
    @DisplayName("댓글 삭제는 POST로 요청한다")
    void delete() throws Exception {
        mockMvc.perform(post(URL + "/9/delete").with(loginMember()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"));

        then(commentService).should().delete(MEMBER_ID, 3L, 11L, 5L, 9L);
    }

    @Test
    @DisplayName("로그인하지 않으면 댓글을 쓰거나 볼 수 없다")
    void unauthenticated() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static CommentItem item() {
        return new CommentItem(9L, "댓글", 70L, "나", true, CREATED_AT);
    }

    private static RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
