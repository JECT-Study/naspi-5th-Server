package kr.ject.naspi.common.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오 로그인과 세션 인증 설정.
 *
 * <p>카카오 인증은 Spring Security OAuth2 로그인이 처리하고, 로그인 정보는 세션에 저장된다.
 * 세션은 Spring Session을 통해 Redis에 보관되어 서버가 여러 대여도 같은 세션을 공유한다.
 *
 * <pre>
 * 프론트 로그인 버튼 → GET /oauth2/authorization/kakao → 카카오 인가 페이지
 *   → GET /login/oauth2/code/kakao (카카오 콜백)
 *   → 기존 회원: 로그인 후 로그인 완료 경로로 이동
 *   → 신규 회원: 카카오 이름으로 자동 가입, 로그인 후 로그인 완료 경로로 이동
 *   → 카카오 이름에 한글이 없는 신규 회원: 가입 대기 상태로 이름 입력 경로로 이동 → POST /api/members/signup
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
public class SecurityConfig {

    /** 로그인하지 않아도 접근할 수 있는 경로. */
    private static final String[] PUBLIC_PATHS = {
        "/oauth2/authorization/**",
        "/login/oauth2/code/**",
        "/api/members/signup",
        "/api/auth/logout",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/error",
    };

    /**
     * H2 콘솔({@code /h2-console}) 접근을 허용한다. 로컬에서 H2 콘솔을 켰을 때만 등록된다.
     *
     * <p>콘솔 화면은 iframe으로 그려지므로 같은 출처의 frame을 허용한다.
     *
     * @param http 보안 설정 빌더
     * @return H2 콘솔 경로 전용 보안 필터 체인
     */
    @Bean
    @Order(0)
    @ConditionalOnBooleanProperty("spring.h2.console.enabled")
    public SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) {
        http
                .securityMatcher("/h2-console/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /**
     * 요청에 적용할 보안 규칙을 정의한다.
     *
     * @param http 보안 설정 빌더
     * @param kakaoLoginSuccessHandler 카카오 인증 성공 후 회원 로그인/가입 대기 처리
     * @param authProperties 로그인 설정
     * @param jsonMapper 401 응답 본문 변환
     * @param securityContextRepository 인증 정보 저장소
     * @return 보안 필터 체인
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationSuccessHandler kakaoLoginSuccessHandler,
            AuthProperties authProperties,
            JsonMapper jsonMapper,
            SecurityContextRepository securityContextRepository) {
        http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository))
                .oauth2Login(oauth2 -> oauth2
                        .authorizedClientRepository(new DiscardingAuthorizedClientRepository())
                        .successHandler(kakaoLoginSuccessHandler)
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler(
                                authProperties.redirect().loginFailureUrl())))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(jsonMapper)))
                // 로그아웃은 공통 응답 형식과 API 문서를 위해 컨트롤러(POST /api/auth/logout)가 처리한다.
                .logout(AbstractHttpConfigurer::disable)
                // 프론트(naspi.my)와 API(api.naspi.my)는 같은 사이트이므로 SameSite=Lax 쿠키를 전제로 비활성화한다.
                // TODO: CSRF 처리 방식 결정 (CookieCsrfTokenRepository 권장).
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * 프론트 출처에서 오는 API 호출을 허용하는 CORS 설정.
     *
     * <p>세션 쿠키를 함께 받도록 자격 증명을 허용한다. 프론트는 요청에
     * {@code credentials: 'include'}를 붙여야 한다.
     *
     * @param corsProperties CORS 설정
     * @return 모든 경로에 적용할 CORS 설정
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 인증 정보를 세션에 저장하는 저장소. 세션은 Spring Session을 통해 Redis에 저장된다.
     *
     * @return 세션 기반 인증 정보 저장소
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
