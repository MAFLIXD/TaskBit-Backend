package com.bitacora.bitacora.service;

import com.bitacora.bitacora.model.Proyecto;
import com.bitacora.bitacora.model.Tarea;
import com.bitacora.bitacora.repository.ProyectoRepository;
import com.bitacora.bitacora.repository.TareaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TareaService {

    private final TareaRepository tareaRepository;
    private final ProyectoRepository proyectoRepository;

    public TareaService(TareaRepository tareaRepository, ProyectoRepository proyectoRepository) {
        this.tareaRepository = tareaRepository;
        this.proyectoRepository = proyectoRepository;
    }

    public List<Tarea> obtenerTodas() {
        return tareaRepository.findAll();
    }

    public Optional<Tarea> obtenerPorId(Long id) {
        return tareaRepository.findById(id);
    }

    @Transactional
    public Tarea guardar(Tarea tarea) {
        Tarea nuevaTarea = tareaRepository.save(tarea);

        // 🔹 Si la tarea está asociada a un proyecto, recalcula su duración total
        if (tarea.getProyecto() != null && tarea.getProyecto().getId() != null) {
            Proyecto proyecto = proyectoRepository.findById(tarea.getProyecto().getId())
                    .orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));

            double totalHoras = proyecto.getTareas().stream()
                    .filter(t -> t.getDuracionHoras() != null)
                    .mapToDouble(Tarea::getDuracionHoras)
                    .sum();

            proyecto.setDuracionHoras(totalHoras);
            proyectoRepository.save(proyecto);
        }

        return nuevaTarea;
    }

    public void eliminar(Long id) {
        tareaRepository.deleteById(id);
    }
}
