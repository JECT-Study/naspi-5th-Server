package kr.ject.naspi.common.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 기본 폼 로그인과 카카오 로그인 설정.
 *
 * <p>로그인 정보는 세션에 저장되고, 세션은 Spring Session을 통해 Redis에 보관된다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 요청에 적용할 보안 규칙을 정의한다.
     *
     * @param http 보안 설정 빌더
     * @return 보안 필터 체인
     * @throws Exception 설정을 구성하지 못한 경우
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 누구나 접근 가능 (로그인 불필요)
                        // .requestMatchers("/public/**").permitAll()
                        // 로그인한 사용자만 접근 가능
                        // .requestMatchers("/private/**").authenticated()
                        // 특정 권한이 필요한 경우 예시
                        // .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 위에 해당하지 않는 나머지 요청은 로그인 필요
                        .anyRequest().authenticated())
                .formLogin(withDefaults())
                .oauth2Login(withDefaults());
        return http.build();
    }
}
