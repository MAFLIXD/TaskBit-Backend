package com.bitacora.bitacora.service;

import com.bitacora.bitacora.model.Proyecto;
import com.bitacora.bitacora.model.Tarea;
import com.bitacora.bitacora.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ProyectoService {

    private final ProyectoRepository proyectoRepository;

    public ProyectoService(ProyectoRepository proyectoRepository) {
        this.proyectoRepository = proyectoRepository;
    }

    public List<Proyecto> obtenerTodos() {
        return proyectoRepository.findAll();
    }

    public Optional<Proyecto> obtenerPorId(Long id) {
        return proyectoRepository.findById(id);
    }

    // 🔹 Guarda el proyecto calculando la duración total automáticamente
    public Proyecto guardar(Proyecto proyecto) {
        recalcularDuracion(proyecto);
        return proyectoRepository.save(proyecto);
    }

    public void eliminar(Long id) {
        proyectoRepository.deleteById(id);
    }

    // ======================= MÉTODO AUXILIAR ==========================
    /**
     * Recalcula la duración total del proyecto.
     * Si tiene tareas asociadas, suma las horas de todas.
     * Si tiene fechas de inicio y fin, usa esas fechas.
     */
    private void recalcularDuracion(Proyecto proyecto) {
        double totalHoras = 0.0;

        if (proyecto.getTareas() != null && !proyecto.getTareas().isEmpty()) {
            for (Tarea tarea : proyecto.getTareas()) {
                // Aseguramos la relación bidireccional
                tarea.setProyecto(proyecto);

                if (tarea.getDuracionHoras() != null) {
                    totalHoras += tarea.getDuracionHoras();
                }
            }
        }

        // Si no hay tareas, dejamos la duración en null (no en 0)
        proyecto.setDuracionHoras(totalHoras > 0 ? totalHoras : null);
    }
}
