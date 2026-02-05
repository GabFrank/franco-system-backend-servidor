package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MarcacionRepository extends JpaRepository<Marcacion, Long> {

    @Query("select m from Marcacion m where m.usuario.id = ?1 and m.fechaEntrada >= ?2 and m.fechaEntrada <= ?3")
    List<Marcacion> findByUsuarioIdAndFechaEntradaBetween(Long usuarioId, LocalDateTime fechaInicio,
            LocalDateTime fechaFin);

    Page<Marcacion> findAll(Pageable pageable);
}
