package com.bitacora.bitacora.controller;

import com.bitacora.bitacora.model.Proyecto;
import com.bitacora.bitacora.model.Tarea;
import com.bitacora.bitacora.service.ProyectoService;
import com.bitacora.bitacora.service.TareaService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tareas")
@CrossOrigin(origins = "*") // 🔹 Permite peticiones desde el frontend (React, etc.)
public class TareaController {

    private final TareaService tareaService;
    private final ProyectoService proyectoService;

    public TareaController(TareaService tareaService, ProyectoService proyectoService) {
        this.tareaService = tareaService;
        this.proyectoService = proyectoService;
    }

    // ====== GET: obtener todas las tareas ======
    @GetMapping
    public List<Tarea> obtenerTodas() {
        return tareaService.obtenerTodas();
    }

    // ====== GET: obtener tarea por ID ======
    @GetMapping("/{id}")
    public Optional<Tarea> obtenerPorId(@PathVariable Long id) {
        return tareaService.obtenerPorId(id);
    }

    // ====== POST: crear nueva tarea ======
    @PostMapping
    public Tarea crearTarea(@RequestBody Tarea tarea) {
        // 🔹 Asegurar que la relación con el proyecto exista antes de guardar
        if (tarea.getProyecto() != null && tarea.getProyecto().getId() != null) {
            Optional<Proyecto> proyectoOpt = proyectoService.obtenerPorId(tarea.getProyecto().getId());
            proyectoOpt.ifPresent(tarea::setProyecto);
        }
        return tareaService.guardar(tarea);
    }

    // ====== PUT: actualizar tarea existente ======
    @PutMapping("/{id}")
    public Tarea actualizarTarea(@PathVariable Long id, @RequestBody Tarea tareaActualizada) {
        Optional<Tarea> tareaExistente = tareaService.obtenerPorId(id);

        if (tareaExistente.isPresent()) {
            Tarea t = tareaExistente.get();

            if (tareaActualizada.getTitulo() != null) t.setTitulo(tareaActualizada.getTitulo());
            if (tareaActualizada.getDescripcion() != null) t.setDescripcion(tareaActualizada.getDescripcion());
            if (tareaActualizada.getEstado() != null) t.setEstado(tareaActualizada.getEstado());
            if (tareaActualizada.getFechaInicio() != null) t.setFechaInicio(tareaActualizada.getFechaInicio());
            if (tareaActualizada.getFechaFin() != null) t.setFechaFin(tareaActualizada.getFechaFin());
            if (tareaActualizada.getDuracionHoras() != null) t.setDuracionHoras(tareaActualizada.getDuracionHoras());
            if (tareaActualizada.getObservaciones() != null) t.setObservaciones(tareaActualizada.getObservaciones());

            // 🔹 Solo reasigna el proyecto si viene un nuevo ID diferente
            if (tareaActualizada.getProyecto() != null && tareaActualizada.getProyecto().getId() != null) {
                Long nuevoProyectoId = tareaActualizada.getProyecto().getId();
                if (t.getProyecto() == null || !t.getProyecto().getId().equals(nuevoProyectoId)) {
                    Optional<Proyecto> proyectoOpt = proyectoService.obtenerPorId(nuevoProyectoId);
                    proyectoOpt.ifPresent(t::setProyecto);
                }
            }

            // 🔹 Guardar la tarea sin afectar las demás tareas del proyecto
            return tareaService.guardar(t);
        } else {
            throw new RuntimeException("Tarea no encontrada con ID: " + id);
        }
    }

    // ====== DELETE: eliminar tarea ======
    @DeleteMapping("/{id}")
    public void eliminarTarea(@PathVariable Long id) {
        tareaService.eliminar(id);
    }
}
