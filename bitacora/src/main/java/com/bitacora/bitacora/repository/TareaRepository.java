package com.bitacora.bitacora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bitacora.bitacora.model.Tarea;

public interface TareaRepository extends JpaRepository<Tarea, Long> {
}
