package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.RecuperacionContrasena;
import com.franco.dev.domain.personas.enums.EstadoRecuperacionContrasena;
import com.franco.dev.domain.personas.enums.TipoRecuperacionContrasena;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

public interface RecuperacionContrasenaRepository extends JpaRepository<RecuperacionContrasena, Long> {

    Page<RecuperacionContrasena> findByTipoAndEstadoInOrderByIdDesc(TipoRecuperacionContrasena tipo,
                                                                    Collection<EstadoRecuperacionContrasena> estados,
                                                                    Pageable pageable);

    Page<RecuperacionContrasena> findByTipoOrderByIdDesc(TipoRecuperacionContrasena tipo, Pageable pageable);

    long countByUsuarioIdAndTipoAndCreadoEnAfter(Long usuarioId, TipoRecuperacionContrasena tipo,
                                                 LocalDateTime desde);

    long countByTipoAndEstadoAndVenceEnAfter(TipoRecuperacionContrasena tipo, EstadoRecuperacionContrasena estado,
                                            LocalDateTime ahora);

    /** Al abrir una nueva o completar una, las otras que seguian abiertas dejan de servir. */
    @Modifying
    @Query("update RecuperacionContrasena r set r.estado = com.franco.dev.domain.personas.enums.EstadoRecuperacionContrasena.ANULADA "
            + "where r.usuario.id = :usuarioId and r.id <> :exceptoId "
            + "and r.estado in (com.franco.dev.domain.personas.enums.EstadoRecuperacionContrasena.PENDIENTE, "
            + "com.franco.dev.domain.personas.enums.EstadoRecuperacionContrasena.APROBADA)")
    int anularAbiertas(@Param("usuarioId") Long usuarioId, @Param("exceptoId") Long exceptoId);
}
