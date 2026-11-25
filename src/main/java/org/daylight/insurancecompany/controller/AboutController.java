package org.daylight.insurancecompany.controller;

import lombok.RequiredArgsConstructor;
import org.daylight.insurancecompany.entity.User;
import org.daylight.insurancecompany.repositories.postgresql.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AboutController {
    private final UserRepository userRepository;

    @GetMapping("/about")
    public String about(@AuthenticationPrincipal UserDetails principal, Model model) {
        if (principal != null) {
            userRepository.findByEmail(principal.getUsername())
                    .ifPresent((User u) -> model.addAttribute("user", u));
        }
        return "about";
    }
}
