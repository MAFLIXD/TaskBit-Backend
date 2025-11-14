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
        Optional<Proyecto> proyectoExistenteOpt = proyectoService.obtenerPorId(id);

        if (proyectoExistenteOpt.isPresent()) {
            Proyecto p = proyectoExistenteOpt.get();

            // 🔹 Solo actualizamos los campos básicos, sin tocar la lista de tareas
            if (proyecto.getNombre() != null) p.setNombre(proyecto.getNombre());
            if (proyecto.getDescripcion() != null) p.setDescripcion(proyecto.getDescripcion());
            if (proyecto.getFechaInicio() != null) p.setFechaInicio(proyecto.getFechaInicio());
            if (proyecto.getFechaFin() != null) p.setFechaFin(proyecto.getFechaFin());

            // 🔹 Si el frontend quiere modificar las tareas, eso debe ir por otro endpoint (por ejemplo: /proyectos/{id}/tareas)
            // 🔹 Aquí evitamos llamar p.setTareas(...)

            // 🔹 Recalcular duración si tiene fechas válidas
            if (p.getFechaInicio() != null && p.getFechaFin() != null) {
                long minutos = Duration.between(p.getFechaInicio(), p.getFechaFin()).toMinutes();
                p.setDuracionHoras(minutos / 60.0);
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
