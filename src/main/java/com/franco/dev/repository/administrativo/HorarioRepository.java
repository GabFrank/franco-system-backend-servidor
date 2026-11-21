package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HorarioRepository extends HelperRepository<Horario, Long> {

    /** Desde V284.3 ya no decide nada: los horarios se asignan por horario_usuario. */
    List<Horario> findByUsuarioIdOrderByIdDesc(Long usuarioId);

    /** Los horarios asignados a la cuenta que marca. */
    @Query(value = "SELECT h.* FROM administrativo.horario h "
            + "JOIN administrativo.horario_usuario hu ON hu.horario_id = h.id "
            + "WHERE hu.usuario_id = :usuarioId ORDER BY h.id DESC", nativeQuery = true)
    List<Horario> findAsignadosAUsuario(@Param("usuarioId") Long usuarioId);

    /** Todos los horarios que se pueden asignar, sin importar a quien. */
    @Query("SELECT h FROM Horario h WHERE (h.activo IS NULL OR h.activo = true) "
            + "AND h.horaEntrada IS NOT NULL ORDER BY h.horaEntrada, h.id")
    List<Horario> findCatalogo();

    /** El mismo catalogo, de a una pagina (#772). */
    @Query(value = "SELECT h FROM Horario h WHERE (h.activo IS NULL OR h.activo = true) "
            + "AND h.horaEntrada IS NOT NULL ORDER BY h.horaEntrada, h.id",
            countQuery = "SELECT count(h) FROM Horario h WHERE (h.activo IS NULL OR h.activo = true) "
            + "AND h.horaEntrada IS NOT NULL")
    Page<Horario> findCatalogo(Pageable pageable);

    @Query(value = "SELECT count(*) FROM administrativo.horario_usuario WHERE horario_id = :horarioId",
            nativeQuery = true)
    long contarAsignados(@Param("horarioId") Long horarioId);

    @Modifying
    @Query(value = "INSERT INTO administrativo.horario_usuario (horario_id, usuario_id) "
            + "VALUES (:horarioId, :usuarioId) ON CONFLICT DO NOTHING", nativeQuery = true)
    int asignar(@Param("horarioId") Long horarioId, @Param("usuarioId") Long usuarioId);

    @Modifying
    @Query(value = "DELETE FROM administrativo.horario_usuario "
            + "WHERE horario_id = :horarioId AND usuario_id = :usuarioId", nativeQuery = true)
    int desasignar(@Param("horarioId") Long horarioId, @Param("usuarioId") Long usuarioId);
}
