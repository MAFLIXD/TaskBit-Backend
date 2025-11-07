package com.bitacora.bitacora.controller;

import com.bitacora.bitacora.model.Tarea;
import com.bitacora.bitacora.service.TareaService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tareas")
@CrossOrigin(origins = "*") // 🔹 Permite peticiones desde el frontend (React, etc.)
public class TareaController {

    private final TareaService tareaService;

    public TareaController(TareaService tareaService) {
        this.tareaService = tareaService;
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
        return tareaService.guardar(tarea);
    }

    // ====== PUT: actualizar tarea existente ======
    @PutMapping("/{id}")
    public Tarea actualizarTarea(@PathVariable Long id, @RequestBody Tarea tareaActualizada) {
        Optional<Tarea> tareaExistente = tareaService.obtenerPorId(id);

        if (tareaExistente.isPresent()) {
            Tarea t = tareaExistente.get();
            t.setTitulo(tareaActualizada.getTitulo());
            t.setDescripcion(tareaActualizada.getDescripcion());
            t.setEstado(tareaActualizada.getEstado());
            t.setFechaInicio(tareaActualizada.getFechaInicio());
            t.setFechaFin(tareaActualizada.getFechaFin());
            t.setDuracionHoras(tareaActualizada.getDuracionHoras());
            t.setObservaciones(tareaActualizada.getObservaciones());
            t.setProyecto(tareaActualizada.getProyecto());

            // 🔹 Al guardar, también se recalculará la duración total del proyecto
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
