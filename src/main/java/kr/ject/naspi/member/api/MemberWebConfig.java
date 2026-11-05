package kr.ject.naspi.member.api;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 회원 모듈의 웹 설정. 가입 대기 정보 세션 접근과 컨트롤러 파라미터 주입을 등록한다.
 *
 * <p>common이 member를 참조하지 않도록 회원 모듈 안에서 등록한다.
 */
@Configuration
public class MemberWebConfig implements WebMvcConfigurer {

    /**
     * 가입 대기 정보 세션 접근을 등록한다.
     *
     * @return 가입 대기 정보 세션 접근
     */
    @Bean
    public SignupSession signupSession() {
        return new SignupSession();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new PendingSignupArgumentResolver(signupSession()));
    }
}
