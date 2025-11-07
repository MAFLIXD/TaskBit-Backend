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

    // 🔹 Guarda el proyecto calculando la duración total en base a sus tareas
    public Proyecto guardar(Proyecto proyecto) {
        if (proyecto.getTareas() != null && !proyecto.getTareas().isEmpty()) {
            double totalHoras = 0.0;
            for (Tarea tarea : proyecto.getTareas()) {
                if (tarea.getDuracionHoras() != null) {
                    totalHoras += tarea.getDuracionHoras();
                }
                // asegura la relación bidireccional
                tarea.setProyecto(proyecto);
            }
            proyecto.setDuracionHoras(totalHoras);
        }
        return proyectoRepository.save(proyecto);
    }

    public void eliminar(Long id) {
        proyectoRepository.deleteById(id);
    }
}
