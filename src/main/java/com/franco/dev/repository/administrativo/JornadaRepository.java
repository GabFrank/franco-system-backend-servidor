package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.repository.HelperRepository;
import com.franco.dev.domain.administrativo.enums.Turno;
import org.springframework.data.domain.Page;
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

    /**
     * De la llegada mas reciente a la mas vieja: por dia y, dentro del dia, por la hora de entrada
     * (la de salida si la entrada no la tiene, igual que el reporte impreso). No por id: el id es
     * por sucursal y no sigue la hora. El LEFT JOIN mantiene las jornadas sin entrada, al final de
     * su dia.
     */
    @Query("SELECT j FROM Jornada j LEFT JOIN j.marcacionEntrada me WHERE j.usuario.id = ?1 " +
            "AND cast(j.fecha as date) >= cast(?2 as date) AND cast(j.fecha as date) <= cast(?3 as date) " +
            "ORDER BY j.fecha DESC, COALESCE(me.fechaEntrada, me.fechaSalida) DESC NULLS LAST, j.id DESC")
    List<Jornada> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin);

    /** Mismo orden que {@link #findByUsuarioIdAndFechaRange}. */
    @Query("SELECT j FROM Jornada j LEFT JOIN j.marcacionEntrada me " +
            "WHERE cast(j.fecha as date) >= cast(?1 as date) AND cast(j.fecha as date) <= cast(?2 as date) " +
            "ORDER BY j.fecha DESC, COALESCE(me.fechaEntrada, me.fechaSalida) DESC NULLS LAST, j.id DESC")
    List<Jornada> findByFechaRange(String fechaInicio, String fechaFin);

    String FILTRO_HISTORIAL = "FROM Jornada j LEFT JOIN j.marcacionEntrada me LEFT JOIN j.marcacionSalida ms " +
            "WHERE cast(j.fecha as date) >= cast(:fechaInicio as date) AND cast(j.fecha as date) <= cast(:fechaFin as date) " +
            "AND (:porUsuario = false OR j.usuario.id = :usuarioId) " +
            "AND (:porTurno = false OR j.turno = :turno) " +
            "AND (:porSucursalEntrada = false OR me.sucursalEntrada.id = :sucursalEntradaId) " +
            "AND (:porSucursalSalida = false OR ms.sucursalSalida.id = :sucursalSalidaId) ";

    /**
     * Una pagina del historial de marcaciones, con el mismo orden que {@link #findByFechaRange}.
     *
     * Cada filtro opcional viaja con su bandera en vez de llegar en null: Postgres no sabe de que tipo es un
     * parametro nulo en un {@code :x IS NULL} y rechaza la consulta.
     */
    @Query(value = "SELECT j " + FILTRO_HISTORIAL +
            "ORDER BY j.fecha DESC, COALESCE(me.fechaEntrada, me.fechaSalida) DESC NULLS LAST, j.id DESC",
            countQuery = "SELECT COUNT(j) " + FILTRO_HISTORIAL)
    Page<Jornada> findHistorial(@Param("fechaInicio") String fechaInicio, @Param("fechaFin") String fechaFin,
            @Param("porUsuario") boolean porUsuario, @Param("usuarioId") Long usuarioId,
            @Param("porTurno") boolean porTurno, @Param("turno") Turno turno,
            @Param("porSucursalEntrada") boolean porSucursalEntrada, @Param("sucursalEntradaId") Long sucursalEntradaId,
            @Param("porSucursalSalida") boolean porSucursalSalida, @Param("sucursalSalidaId") Long sucursalSalidaId,
            Pageable pageable);

    /** Una fila: dias distintos, minutos trabajados, extras y de llegada tardia de todo el filtro. */
    @Query("SELECT COUNT(DISTINCT j.fecha), COALESCE(SUM(j.minutosTrabajados), 0), " +
            "COALESCE(SUM(j.minutosExtras), 0), COALESCE(SUM(j.minutosLlegadaTardia), 0) " + FILTRO_HISTORIAL)
    List<Object[]> resumenHistorial(@Param("fechaInicio") String fechaInicio, @Param("fechaFin") String fechaFin,
            @Param("porUsuario") boolean porUsuario, @Param("usuarioId") Long usuarioId,
            @Param("porTurno") boolean porTurno, @Param("turno") Turno turno,
            @Param("porSucursalEntrada") boolean porSucursalEntrada, @Param("sucursalEntradaId") Long sucursalEntradaId,
            @Param("porSucursalSalida") boolean porSucursalSalida, @Param("sucursalSalidaId") Long sucursalSalidaId);

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
