package com.inventario1.Inventario.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.stereotype.Controller;
import com.inventario1.Inventario.services.EmailService;

@Controller
class EmailController {
    final EmailService emailService;

    EmailController(EmailService emailService) { this.emailService = emailService; }

    @GetMapping("/test-email")
    String test(@RequestParam String to) {
        emailService.enviarTexto(to, "Prueba SMTP", "Conexión OK.");
        return "redirect:/?email=ok";
    }
}
