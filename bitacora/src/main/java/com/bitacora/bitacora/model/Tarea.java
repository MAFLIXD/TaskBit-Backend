package com.bitacora.bitacora.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.Duration;

@Entity
@Table(name = "tareas")
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;
    private String descripcion;
    private String estado; // Pendiente, En progreso, Completada
    private String proyecto; // Nombre del proyecto o categoría

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Double duracionHoras; // Calculada automáticamente

    private String observaciones;

    private LocalDateTime fechaCreacion = LocalDateTime.now();

    // ====== Getters y Setters ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getProyecto() { return proyecto; }
    public void setProyecto(String proyecto) { this.proyecto = proyecto; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public Double getDuracionHoras() { return duracionHoras; }
    public void setDuracionHoras(Double duracionHoras) { this.duracionHoras = duracionHoras; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    // ====== Método auxiliar ======
    @PrePersist
    @PreUpdate
    public void calcularDuracion() {
        if (fechaInicio != null && fechaFin != null) {
            long minutos = Duration.between(fechaInicio, fechaFin).toMinutes();
            this.duracionHoras = minutos / 60.0;
        }
    }
}
