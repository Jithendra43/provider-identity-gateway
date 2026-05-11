package chit.tefca.directory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@org.springframework.context.annotation.Profile("!prod")
@EnableWebSecurity
public class SecurityConfig {

    @Bean("directorySecurityFilterChain")
    @org.springframework.core.annotation.Order(12)
    public SecurityFilterChain directorySecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/directory/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
