package com.bitacora.bitacora.controller;

import com.bitacora.bitacora.model.Proyecto;
import com.bitacora.bitacora.service.ProyectoService;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/proyectos")
@CrossOrigin(origins = "*") // 🔹 Permite peticiones desde el frontend
public class ProyectoController {

    private final ProyectoService proyectoService;

    public ProyectoController(ProyectoService proyectoService) {
        this.proyectoService = proyectoService;
    }

    // ====== GET: obtener todos los proyectos ======
    @GetMapping
    public List<Proyecto> obtenerTodos() {
        return proyectoService.obtenerTodos();
    }

    // ====== GET: obtener un proyecto por ID ======
    @GetMapping("/{id}")
    public Optional<Proyecto> obtenerPorId(@PathVariable Long id) {
        return proyectoService.obtenerPorId(id);
    }

    // ====== POST: crear un nuevo proyecto ======
    @PostMapping
    public Proyecto crearProyecto(@RequestBody Proyecto proyecto) {
        return proyectoService.guardar(proyecto);
    }

    // ====== PUT: actualizar un proyecto existente ======
    @PutMapping("/{id}")
public Proyecto actualizarProyecto(@PathVariable Long id, @RequestBody Proyecto proyecto) {
    Optional<Proyecto> proyectoExistente = proyectoService.obtenerPorId(id);

    if (proyectoExistente.isPresent()) {
        Proyecto p = proyectoExistente.get();
        p.setNombre(proyecto.getNombre());
        p.setDescripcion(proyecto.getDescripcion());
        p.setFechaInicio(proyecto.getFechaInicio());
        p.setFechaFin(proyecto.getFechaFin());

        // 🔹 Calcula la duración antes de guardar
        if (p.getFechaInicio() != null && p.getFechaFin() != null) {
            long minutos = Duration.between(p.getFechaInicio(), p.getFechaFin()).toMinutes();
            p.setDuracionHoras(minutos / 60.0);
        } else {
            p.setDuracionHoras(null);
        }

        return proyectoService.guardar(p);
    } else {
        throw new RuntimeException("Proyecto no encontrado con ID: " + id);
    }
}

    // ====== DELETE: eliminar un proyecto por ID ======
    @DeleteMapping("/{id}")
    public void eliminarProyecto(@PathVariable Long id) {
        proyectoService.eliminar(id);
    }
}
