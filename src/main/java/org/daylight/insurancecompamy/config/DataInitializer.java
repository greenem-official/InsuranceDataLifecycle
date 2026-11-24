package org.daylight.insurancecompamy.config;

import lombok.RequiredArgsConstructor;
import org.daylight.insurancecompamy.dto.Role;
import org.daylight.insurancecompamy.entity.User;
import org.daylight.insurancecompamy.repositories.postgresql.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    @Bean
    CommandLineRunner initUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            createIfNotExists(userRepository, passwordEncoder,
                    "owner@insurance.local", "1234", Role.OWNER, "Иван Владельцев");
            createIfNotExists(userRepository, passwordEncoder,
                    "steward@insurance.local", "1234", Role.STEWARD, "Анна Стюардова");
            createIfNotExists(userRepository, passwordEncoder,
                    "admin@insurance.local", "1234", Role.ADMIN, "Пётр Админов");
            createIfNotExists(userRepository, passwordEncoder,
                    "auditor@insurance.local", "1234", Role.AUDITOR, "Мария Аудитор");
        };
    }

    private void createIfNotExists(UserRepository repo, PasswordEncoder encoder,
                                   String email, String rawPass, Role role, String fullName) {
        if (repo.findByEmail(email).isEmpty()) {
            repo.save(User.builder()
                    .email(email)
                    .passwordHash(encoder.encode(rawPass))
                    .role(role)
                    .fullName(fullName)
                    .build());
        }
    }
}
