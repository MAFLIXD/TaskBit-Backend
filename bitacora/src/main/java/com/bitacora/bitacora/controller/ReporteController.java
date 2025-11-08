package com.bitacora.bitacora.controller;

import com.bitacora.bitacora.model.Proyecto;
import com.bitacora.bitacora.service.ProyectoService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reportes")
@CrossOrigin(origins = "*")
public class ReporteController {

    private final ProyectoService proyectoService;

    public ReporteController(ProyectoService proyectoService) {
        this.proyectoService = proyectoService;
    }

    @GetMapping("/proyectos")
    public List<Map<String, Object>> obtenerReporteProyectos() {
        List<Proyecto> proyectos = proyectoService.obtenerTodos();

        return proyectos.stream()
                .map(p -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", p.getId());
                    data.put("nombre", p.getNombre());
                    data.put("totalHoras", p.getDuracionHoras());
                    data.put("tareasTotales", p.getTareas() != null ? p.getTareas().size() : 0);
                    data.put("tareasCompletadas", p.getTareas() != null
                            ? p.getTareas().stream().filter(t -> "Completada".equalsIgnoreCase(t.getEstado())).count()
                            : 0);

                    // 👇 adicional: porcentaje de progreso
                    int total = (p.getTareas() != null ? p.getTareas().size() : 0);
                    long completadas = (p.getTareas() != null
                            ? p.getTareas().stream().filter(t -> "Completada".equalsIgnoreCase(t.getEstado())).count()
                            : 0);
                    double progreso = total > 0 ? (completadas * 100.0 / total) : 0.0;
                    data.put("progreso", progreso);

                    return data;
                })
                .collect(Collectors.toList());
    }
}
