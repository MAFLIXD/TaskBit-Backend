package com.bitacora.bitacora.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.bitacora.bitacora.model.Tarea;

@Repository
public interface TareaRepository extends JpaRepository<Tarea, Long> {
}
