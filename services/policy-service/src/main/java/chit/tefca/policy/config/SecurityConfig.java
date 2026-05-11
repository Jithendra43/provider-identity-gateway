package chit.tefca.policy.config;

import chit.tefca.common.security.HmacVerificationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@org.springframework.context.annotation.Profile("!prod")
@EnableWebSecurity
public class SecurityConfig {

    @Value("${tefca.hmac.secret:}")
    private String hmacSecret;

    /**
     * HMAC verification for inbound calls from ingress-auth-service. When the
     * shared secret is blank (test/dev profile) verification is skipped.
     */
    @Bean
    public HmacVerificationFilter hmacVerificationFilter() {
        return new HmacVerificationFilter(hmacSecret, List.of("/api/v1/policy", "/api/v1/admin"));
    }

    @Bean("policySecurityFilterChain")
    @org.springframework.core.annotation.Order(10)
    public SecurityFilterChain policySecurityFilterChain(HttpSecurity http,
                                                         HmacVerificationFilter hmacFilter) throws Exception {
        http
            .securityMatcher("/api/v1/policy/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .addFilterBefore(hmacFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
