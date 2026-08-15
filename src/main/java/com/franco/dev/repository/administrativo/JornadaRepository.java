package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JornadaRepository extends HelperRepository<Jornada, EmbebedPrimaryKey> {

    default Class<Jornada> getEntityClass() {
        return Jornada.class;
    }

    @Query("SELECT MAX(m.id) FROM Jornada m WHERE m.sucursalId = :sucursalId")
    Long findMaxId(@Param("sucursalId") Long sucursalId);

    List<Jornada> findByUsuarioId(Long usuarioId);

    /**
     * Jornadas de un usuario, de la mas reciente a la mas antigua, paginadas.
     *
     * Existe porque hay una fila por dia trabajado: ~250 al ano por
     * funcionario, creciendo para siempre. Traerlas todas para mostrar las
     * ultimas veinte hace que el costo de abrir la pantalla crezca con la
     * antiguedad del empleado.
     */
    List<Jornada> findByUsuarioIdOrderByFechaDescIdDesc(Long usuarioId, Pageable pageable);

    @Query("SELECT j FROM Jornada j WHERE j.usuario.id = ?1 " +
            "AND cast(j.fecha as date) >= cast(?2 as date) AND cast(j.fecha as date) <= cast(?3 as date) ORDER BY j.id DESC")
    List<Jornada> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin);

    @Query("SELECT j FROM Jornada j WHERE cast(j.fecha as date) >= cast(?1 as date) AND cast(j.fecha as date) <= cast(?2 as date) ORDER BY j.id DESC")
    List<Jornada> findByFechaRange(String fechaInicio, String fechaFin);

    @Query("SELECT j FROM Jornada j WHERE j.usuario.id = ?1 AND cast(j.fecha as date) = cast(?2 as date) ORDER BY j.id ASC")
    List<Jornada> findByUsuarioIdAndFecha(Long usuarioId, String fecha);

    Optional<Jornada> findByMarcacionEntradaId(Long id);

    @Query("SELECT j FROM Jornada j WHERE j.usuario.id = :usuarioId " +
            "AND j.estado = 'INCOMPLETO' AND j.marcacionSalida IS NULL " +
            "AND (j.turno IN ('NOCHE', 'MADRUGADA') " +
            "OR (j.horaEntradaHorario IS NOT NULL AND j.horaSalidaHorario IS NOT NULL " +
            "AND j.horaSalidaHorario < j.horaEntradaHorario)) " +
            "ORDER BY j.fecha DESC, j.id DESC")
    List<Jornada> findIncompletasSinSalidaNocturnasByUsuarioId(@Param("usuarioId") Long usuarioId);

    /** Jornada abierta con entrada registrada, sin importar sucursal_id de la jornada ni de la marcación. */
    @Query("SELECT j FROM Jornada j WHERE j.usuario.id = :usuarioId " +
            "AND cast(j.fecha as date) = cast(:fecha as date) " +
            "AND j.estado = 'INCOMPLETO' AND j.marcacionSalida IS NULL " +
            "AND j.marcacionEntrada IS NOT NULL " +
            "ORDER BY j.id DESC")
    List<Jornada> findAbiertasConEntradaSinSalidaByUsuarioIdAndFecha(
            @Param("usuarioId") Long usuarioId,
            @Param("fecha") String fecha);

    /** Jornada abierta sin entrada (pendiente de primera marcación del día). */
    @Query("SELECT j FROM Jornada j WHERE j.usuario.id = :usuarioId " +
            "AND cast(j.fecha as date) = cast(:fecha as date) " +
            "AND j.estado = 'INCOMPLETO' AND j.marcacionSalida IS NULL " +
            "AND j.marcacionEntrada IS NULL " +
            "ORDER BY j.id DESC")
    List<Jornada> findAbiertasSinEntradaByUsuarioIdAndFecha(
            @Param("usuarioId") Long usuarioId,
            @Param("fecha") String fecha);

}
