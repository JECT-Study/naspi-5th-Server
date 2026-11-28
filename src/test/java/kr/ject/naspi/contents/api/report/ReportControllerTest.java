package kr.ject.naspi.contents.api.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.contents.application.ImageFile;
import kr.ject.naspi.contents.application.ReportService;
import kr.ject.naspi.contents.application.SubmittedReport;
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
@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URL = "/api/leagues/3/stocks/11/reports";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("제보 작성은 multipart로 본문과 이미지를 받아 작성된 제보를 돌려준다")
    void submit() throws Exception {
        given(reportService.submit(eq(MEMBER_ID), eq(3L), eq(11L), eq("본문"), anyList()))
                .willReturn(new SubmittedReport(21L, 11L, "본문", 2,
                        LocalDateTime.of(2026, 11, 17, 10, 0), List.of(31L, 32L), 41L));

        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("images", "a.png", "image/png", PNG))
                        .file(new MockMultipartFile("images", "b.png", "image/png", PNG))
                        .param("body", "본문")
                        .with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.reportId").value(21))
                .andExpect(jsonPath("$.data.stockId").value(11))
                .andExpect(jsonPath("$.data.body").value("본문"))
                .andExpect(jsonPath("$.data.tradingDay").value(2))
                .andExpect(jsonPath("$.data.imageIds[0]").value(31))
                .andExpect(jsonPath("$.data.imageIds[1]").value(32))
                .andExpect(jsonPath("$.data.newsId").value(41));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImageFile>> images = ArgumentCaptor.forClass(List.class);
        then(reportService).should().submit(anyLong(), anyLong(), anyLong(), any(),
                images.capture());
        assertThat(images.getValue()).extracting(ImageFile::originalFilename,
                        ImageFile::contentType)
                .containsExactly(tuple("a.png", "image/png"), tuple("b.png", "image/png"));
    }

    @Test
    @DisplayName("이름 없는 빈 이미지 파트는 첨부하지 않은 것으로 본다")
    void emptyImagePart() throws Exception {
        given(reportService.submit(eq(MEMBER_ID), eq(3L), eq(11L), eq("본문"), eq(List.of())))
                .willReturn(new SubmittedReport(21L, 11L, "본문", 1,
                        LocalDateTime.of(2026, 11, 17, 10, 0), List.of(), 41L));

        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("images", "", "application/octet-stream",
                                new byte[0]))
                        .param("body", "본문")
                        .with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageIds").isEmpty());
    }

    @Test
    @DisplayName("자기 종목에 제보하면 비즈니스 오류 코드로 응답한다")
    void ownStock() throws Exception {
        given(reportService.submit(anyLong(), anyLong(), anyLong(), any(), anyList()))
                .willThrow(new NaspiBizException(ErrorCode.REPORT_OWN_STOCK));

        mockMvc.perform(multipart(URL).param("body", "본문").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("620"));
    }

    @Test
    @DisplayName("로그인하지 않으면 제보를 작성할 수 없다")
    void unauthenticated() throws Exception {
        mockMvc.perform(multipart(URL).param("body", "본문"))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
