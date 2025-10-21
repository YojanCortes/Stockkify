package com.inventario1.Inventario.services;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    final JavaMailSender mail;

    public EmailService(JavaMailSender mail) { this.mail = mail; }

    public void enviarTexto(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mail.send(msg);
    }

    public void enviarHtml(String to, String subject, String html) throws Exception {
        MimeMessage msg = mail.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mail.send(msg);
    }
}
