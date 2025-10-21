package com.inventario1.Inventario.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.stereotype.Controller;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.services.EmailService;
import java.util.List;

@Controller
class AlertasEmailController {
    final ProductoRepository productoRepository;
    final EmailService emailService;

    AlertasEmailController(ProductoRepository productoRepository, EmailService emailService) {
        this.productoRepository = productoRepository;
        this.emailService = emailService;
    }

    @GetMapping("/alertas/criticos/email")
    String enviarCriticos(@RequestParam String to) throws Exception {
        List<Producto> criticos = productoRepository.findCriticos();
        StringBuilder html = new StringBuilder();
        html.append("<h2>Productos cr&iacute;ticos</h2>");
        if (criticos.isEmpty()) {
            html.append("<p>No hay productos en estado cr&iacute;tico.</p>");
        } else {
            html.append("<table border='1' cellspacing='0' cellpadding='6'>")
                    .append("<tr><th>ID</th><th>Nombre</th><th>Código</th><th>Stock</th><th>Mínimo</th></tr>");
            for (Producto p : criticos) {
                html.append("<tr>")
                        .append("<td>").append(p.getId()).append("</td>")
                        .append("<td>").append(esc(p.getNombre())).append("</td>")
                        .append("<td>").append(esc(p.getCodigoBarras())).append("</td>")
                        .append("<td>").append(n(p.getStockActual())).append("</td>")
                        .append("<td>").append(n(p.getStockMinimo())).append("</td>")
                        .append("</tr>");
            }
            html.append("</table>");
        }
        emailService.enviarHtml(to, "Alerta: Productos críticos", html.toString());
        return "redirect:/?criticosEmail=ok";
    }

    static String esc(String s){ return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
    static int n(Integer v){ return v==null?0:v; }
}
