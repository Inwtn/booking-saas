package dev.isaacalmeida.booking;

import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService users(JdbcTemplate jdbc) {
        return email -> jdbc.query("SELECT email,password_hash FROM app_users WHERE email=?",
                (rs,n) -> User.withUsername(rs.getString("email")).password(rs.getString("password_hash")).roles("USER").build(),
                email.trim().toLowerCase(Locale.ROOT)).stream().findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/index.html", "/app.js", "/style.css", "/api/csrf", "/api/register").permitAll()
                    .anyRequest().authenticated())
                .formLogin(form -> form.loginProcessingUrl("/api/login")
                    .successHandler((req,res,auth) -> res.setStatus(204))
                    .failureHandler((req,res,ex) -> res.sendError(401,"Invalid credentials")))
                .logout(logout -> logout.logoutUrl("/api/logout").logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((req,res,ex) -> res.sendError(401)))
                .build();
    }
}
