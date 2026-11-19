package kr.ject.naspi.contents.api.reaction;

import static kr.ject.naspi.contents.domain.DisclosureReactionType.DISLIKE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.contents.application.DisclosureReactionService;
import kr.ject.naspi.contents.application.DisclosureReactionSummary;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
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
@WebMvcTest(DisclosureReactionController.class)
@Import(SecurityConfig.class)
class DisclosureReactionControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URL = "/api/leagues/3/stocks/11/disclosures/5/reaction";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DisclosureReactionService reactionService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("누른 반응 버튼만 보내면 토글 후 집계와 내 반응을 돌려준다")
    void react() throws Exception {
        given(reactionService.toggle(MEMBER_ID, 3L, 11L, 5L, DISLIKE))
                .willReturn(new DisclosureReactionSummary(5L, 2, 1, DISLIKE));

        mockMvc.perform(post(URL).with(loginMember()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"DISLIKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.disclosureId").value(5))
                .andExpect(jsonPath("$.data.likeCount").value(2))
                .andExpect(jsonPath("$.data.dislikeCount").value(1))
                .andExpect(jsonPath("$.data.myReaction").value("DISLIKE"));
    }

    @Test
    @DisplayName("반응 구분이 없거나 호재·악재가 아니면 HTTP 400으로 거절한다")
    void invalidType() throws Exception {
        mockMvc.perform(post(URL).with(loginMember()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL).with(loginMember()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"HEART\"}"))
                .andExpect(status().isBadRequest());

        then(reactionService).should(never()).toggle(anyLong(), anyLong(), anyLong(), anyLong(),
                any());
    }

    @Test
    @DisplayName("같은 버튼을 다시 눌러 반응이 취소되면 내 반응은 비어 있다")
    void toggledOff() throws Exception {
        given(reactionService.toggle(MEMBER_ID, 3L, 11L, 5L, DISLIKE))
                .willReturn(new DisclosureReactionSummary(5L, 1, 0, null));

        mockMvc.perform(post(URL).with(loginMember()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"DISLIKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.myReaction").doesNotExist());
    }

    @Test
    @DisplayName("진행 중이 아닌 리그면 비즈니스 오류 코드로 응답한다")
    void leagueNotInProgress() throws Exception {
        given(reactionService.toggle(anyLong(), anyLong(), anyLong(), anyLong(), any()))
                .willThrow(new NaspiBizException(ErrorCode.DISCLOSURE_REACTION_LEAGUE_NOT_STARTED));

        mockMvc.perform(post(URL).with(loginMember()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"LIKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("609"));
    }

    @Test
    @DisplayName("로그인하지 않으면 공시에 반응할 수 없다")
    void unauthenticated() throws Exception {
        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"LIKE\"}"))
                .andExpect(status().isUnauthorized());
        then(reactionService).should(never()).toggle(anyLong(), anyLong(), anyLong(), anyLong(),
                any());
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
