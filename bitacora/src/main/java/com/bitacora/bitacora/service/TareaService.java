package com.bitacora.bitacora.service;

import com.bitacora.bitacora.model.Tarea;
import com.bitacora.bitacora.repository.TareaRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.List;

@Service
public class TareaService {
    private final TareaRepository tareaRepository;

    public TareaService(TareaRepository tareaRepository) {
        this.tareaRepository = tareaRepository;
    }

    public Optional<Tarea> obtenerPorId(Long id) {
    return tareaRepository.findById(id);
    }

    public List<Tarea> obtenerTodas() {
        return tareaRepository.findAll();
    }

    public Tarea guardar(Tarea tarea) {
        return tareaRepository.save(tarea);
    }

    public void eliminar(Long id) {
        tareaRepository.deleteById(id);
    }
}
