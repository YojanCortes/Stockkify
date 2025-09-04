package com.inventario1.Inventario.models;


import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "movimientos")
public class Movimiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String tipo; // SALIDA

    @Column(length = 120) private String motivo;
    @Column(length = 120) private String referencia;
    @Column(length = 100) private String usuario;

    @Column(name="creado_en", nullable=false)
    private Instant creadoEn = Instant.now();

    // getters/setters
    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
}
