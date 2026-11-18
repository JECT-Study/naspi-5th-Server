package kr.ject.naspi.contents.api.disclosure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.contents.application.DisclosureService;
import kr.ject.naspi.contents.application.ImageFile;
import kr.ject.naspi.contents.application.PublishedDisclosure;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ActiveProfiles("test")
@WebMvcTest(DisclosureController.class)
@Import(SecurityConfig.class)
class DisclosureControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URL = "/api/leagues/3/stocks/11/disclosures";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DisclosureService disclosureService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("상장자는 제목·본문과 이미지를 multipart로 보내 공시를 작성한다")
    void publish() throws Exception {
        given(disclosureService.publish(eq(MEMBER_ID), eq(3L), eq(11L), eq("제목"), eq("본문"),
                anyList()))
                .willReturn(new PublishedDisclosure(21L, 11L, "제목", "본문", 2,
                        LocalDateTime.of(2026, 11, 17, 10, 0), List.of(31L, 32L)));

        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("images", "a.png", "image/png", PNG))
                        .file(new MockMultipartFile("images", "b.png", "image/png", PNG))
                        .param("title", "제목").param("body", "본문")
                        .with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.disclosureId").value(21))
                .andExpect(jsonPath("$.data.tradingDay").value(2))
                .andExpect(jsonPath("$.data.imageIds[0]").value(31))
                .andExpect(jsonPath("$.data.imageIds[1]").value(32));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImageFile>> images = ArgumentCaptor.forClass(List.class);
        then(disclosureService).should().publish(anyLong(), anyLong(), anyLong(), any(), any(),
                images.capture());
        assertThat(images.getValue()).extracting(ImageFile::originalFilename,
                        ImageFile::contentType)
                .containsExactly(tuple("a.png", "image/png"),
                        tuple("b.png", "image/png"));
    }

    @Test
    @DisplayName("이미지 없이도 공시를 작성할 수 있다")
    void publishWithoutImages() throws Exception {
        given(disclosureService.publish(eq(MEMBER_ID), eq(3L), eq(11L), eq("제목"), eq("본문"),
                eq(List.of())))
                .willReturn(new PublishedDisclosure(21L, 11L, "제목", "본문", 1,
                        LocalDateTime.of(2026, 11, 17, 10, 0), List.of()));

        mockMvc.perform(multipart(URL).param("title", "제목").param("body", "본문")
                        .with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageIds").isEmpty());
    }

    @Test
    @DisplayName("본인이 상장하지 않은 종목이면 비즈니스 오류 코드로 응답한다")
    void notOwned() throws Exception {
        given(disclosureService.publish(anyLong(), anyLong(), anyLong(), any(), any(), anyList()))
                .willThrow(new NaspiBizException(ErrorCode.DISCLOSURE_STOCK_NOT_OWNED));

        mockMvc.perform(multipart(URL).param("title", "제목").param("body", "본문")
                        .with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("605"));
    }

    @Test
    @DisplayName("로그인하지 않으면 공시를 작성할 수 없다")
    void unauthenticated() throws Exception {
        mockMvc.perform(multipart(URL).param("title", "제목").param("body", "본문"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("공시 작성자는 POST /disclosures/{id}/delete로 공시를 삭제한다")
    void delete() throws Exception {
        mockMvc.perform(post(URL + "/5/delete").with(loginMember()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"));

        then(disclosureService).should().delete(MEMBER_ID, 3L, 11L, 5L);
    }

    @Test
    @DisplayName("공시 작성자가 아니면 비즈니스 오류 코드로 응답한다")
    void deleteNotAuthor() throws Exception {
        willThrow(new NaspiBizException(ErrorCode.DISCLOSURE_NOT_AUTHOR))
                .given(disclosureService).delete(anyLong(), anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(URL + "/5/delete").with(loginMember()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("608"));
    }

    @Test
    @DisplayName("로그인하지 않으면 공시를 삭제할 수 없다")
    void deleteUnauthenticated() throws Exception {
        mockMvc.perform(post(URL + "/5/delete").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
