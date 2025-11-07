package com.bitacora.bitacora.controller;
import com.bitacora.bitacora.model.Tarea;
import com.bitacora.bitacora.service.TareaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/tareas")
@CrossOrigin(origins = "*")
public class TareaController {

    private final TareaService tareaService;

    public TareaController(TareaService tareaService) {
        this.tareaService = tareaService;
    }

    // Listar todas las tareas
    @GetMapping
    public List<Tarea> listarTareas() {
        return tareaService.obtenerTodas();
    }

    // Obtener tarea por id
    @GetMapping("/{id}")
    public ResponseEntity<Tarea> obtenerTareaPorId(@PathVariable Long id) {
        return tareaService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Crear tarea (calcula duración si vienen fechas)
    @PostMapping
    public ResponseEntity<Tarea> crearTarea(@RequestBody Tarea tarea) {
        calcularDuracionSiEsPosible(tarea);
        Tarea creada = tareaService.guardar(tarea);
        return ResponseEntity.status(201).body(creada);
    }

    // Actualizar tarea completa (PUT)
    @PutMapping("/{id}")
    public ResponseEntity<Tarea> actualizarTarea(@PathVariable Long id, @RequestBody Tarea tarea) {
        return tareaService.obtenerPorId(id).map(existing -> {
            // Actualiza campos (puedes adaptar qué campos permiten actualizarse)
            existing.setTitulo(tarea.getTitulo());
            existing.setDescripcion(tarea.getDescripcion());
            existing.setEstado(tarea.getEstado());
            existing.setProyecto(tarea.getProyecto());
            existing.setFechaInicio(tarea.getFechaInicio());
            existing.setFechaFin(tarea.getFechaFin());
            existing.setObservaciones(tarea.getObservaciones());
            calcularDuracionSiEsPosible(existing);
            Tarea updated = tareaService.guardar(existing);
            return ResponseEntity.ok(updated);
        }).orElse(ResponseEntity.notFound().build());
    }

    // Eliminar tarea
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarTarea(@PathVariable Long id) {
        if (tareaService.obtenerPorId(id).isPresent()) {
            tareaService.eliminar(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // ----- Auxiliar: calcula duración en horas si fechaInicio + fechaFin están presentes -----
    private void calcularDuracionSiEsPosible(Tarea tarea) {
        if (tarea.getFechaInicio() != null && tarea.getFechaFin() != null) {
            long minutos = Duration.between(tarea.getFechaInicio(), tarea.getFechaFin()).toMinutes();
            tarea.setDuracionHoras(minutos / 60.0);
        } else {
            tarea.setDuracionHoras(null);
        }
    }
}
