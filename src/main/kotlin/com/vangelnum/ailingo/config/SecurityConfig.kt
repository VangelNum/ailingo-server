package com.vangelnum.ailingo.config

import com.vangelnum.ailingo.user.repository.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val userRepository: UserRepository
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(): UserDetailsService {
        return UserDetailsService { username ->
            val userByEmail = userRepository.findByEmail(username).orElse(null)
            if (userByEmail != null) {
                return@UserDetailsService User(
                    userByEmail.email,
                    userByEmail.password,
                    listOf(GrantedAuthority { "ROLE_${userByEmail.role}" })
                )
            }

            val userByName = userRepository.findByName(username).orElseThrow {
                UsernameNotFoundException("User not found with username or email: $username")
            }

            User(
                userByName.email,
                userByName.password,
                listOf(GrantedAuthority { "ROLE_${userByName.role}" })
            )
        }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf(
            "http://localhost:8080",
            "https://localhost:8080",
            "https://vangelnum.github.io",
            "https://ailingo-vangel.amvera.io",
            "https://ailingo.netlify.app"
        )
        configuration.allowedMethods = listOf(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        )
        configuration.allowedHeaders = listOf(
            "Authorization", "Cache-Control", "Content-Type", "Accept"
        )
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/v1/**", configuration)
        return source
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/v1/user/register").permitAll()
                    .requestMatchers("/api/v1/user/verify-email").permitAll()
                    .requestMatchers("/api/v1/user/resend-verification-code").permitAll()
                    .requestMatchers("/api/v1/generate/image/**").permitAll()
                    .requestMatchers("/api/v1/dictionary/define").permitAll()
                    .requestMatchers("/api/v1/upload/**").permitAll()
                    .requestMatchers("/swagger-ui/**").permitAll()
                    .requestMatchers("/v3/api-docs/**").permitAll()
                    .requestMatchers("/swagger-resources/**").permitAll()
                    .anyRequest().authenticated()
            }
            .httpBasic {}

        return http.build()
    }
}