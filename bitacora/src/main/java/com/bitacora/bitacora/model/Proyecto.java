package com.bitacora.bitacora.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "proyectos")
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Double duracionHoras; // Calculada automáticamente
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    // Relación uno a muchos: un proyecto tiene muchas tareas
    @OneToMany(mappedBy = "proyecto", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference  // 👈 evita el bucle con las tareas
    private List<Tarea> tareas;

    // ===================== Getters y Setters =====================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public Double getDuracionHoras() { return duracionHoras; }
    public void setDuracionHoras(Double duracionHoras) { this.duracionHoras = duracionHoras; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public List<Tarea> getTareas() { return tareas; }
    public void setTareas(List<Tarea> tareas) { this.tareas = tareas; }

    // ===================== Métodos auxiliares =====================
    /**
     * Calcula automáticamente la duración del proyecto:
     * 1️⃣ Si tiene fechaInicio y fechaFin → usa esas fechas.
     * 2️⃣ Si tiene tareas → suma las horas de las tareas.
     */
    @PrePersist
    @PreUpdate
    public void calcularDuracion() {
        if (fechaInicio != null && fechaFin != null) {
            // Si el proyecto tiene fechas, calcula diferencia directa
            long minutos = Duration.between(fechaInicio, fechaFin).toMinutes();
            this.duracionHoras = minutos / 60.0;
        } else if (tareas != null && !tareas.isEmpty()) {
            // Si tiene tareas asociadas, suma sus horas
            double total = tareas.stream()
                    .filter(t -> t.getDuracionHoras() != null)
                    .mapToDouble(Tarea::getDuracionHoras)
                    .sum();
            this.duracionHoras = total;
        } else {
            this.duracionHoras = null;
        }
    }
}
