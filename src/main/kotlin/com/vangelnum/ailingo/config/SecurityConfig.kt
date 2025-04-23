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
            val user = userRepository.findByEmail(username).orElse(null)

            if (user == null) {
                val userByName = userRepository.findByName(username).orElse(null)
                    ?: throw UsernameNotFoundException("User not found with username or email: $username")

                userByName.let {
                    User(
                        it.email,
                        it.password,
                        listOf(GrantedAuthority { "ROLE_${it.role}" })
                    )
                }
            } else {
                user.let {
                    User(
                        it.email,
                        it.password,
                        listOf(GrantedAuthority { "ROLE_${it.role}" })
                    )
                }
            }
        }
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
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