package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.RetiroCaso;
import com.franco.dev.domain.financiero.enums.EstadoCasoRetiro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RetiroCasoRepository extends JpaRepository<RetiroCaso, Long> {

    Optional<RetiroCaso> findByRetiroIdAndSucursalId(Long retiroId, Long sucursalId);

    /**
     * El caso de una verificación puntual.
     *
     * Un mismo retiro puede acumular varios casos: si se anula la verificación, el retiro
     * vuelve a flotar y al recontarlo con diferencia se abre otro. Buscar por (retiro,
     * sucursal) devolvía más de una fila y reventaba con IncorrectResultSizeDataAccessException,
     * dejando la anulación rota para ese retiro hasta tocar la base a mano.
     */
    Optional<RetiroCaso> findByVerificacionId(Long verificacionId);

    /**
     * Cierra sin veredicto el caso de una verificación que se anuló, <b>solo si sigue sin resolver</b>.
     *
     * <p>Es un UPDATE dirigido y no «leer, modificar y guardar»: guardar la entidad escribe todas sus
     * columnas con lo que se leyó antes, y si alguien resolvía el caso en ese momento le pisaba el
     * veredicto, el responsable y el informe. Acá PostgreSQL espera el lock de esa resolución y vuelve a
     * evaluar el WHERE al despertar: un caso recién resuelto queda afuera (issue #376).</p>
     *
     * @return cuántos casos cerró (0 si ya estaba resuelto, o si la verificación no abrió caso)
     */
    @Modifying
    @Query("update RetiroCaso c set c.estado = :resuelto, c.resolucion = :resolucion, "
            + "c.resueltoPor = :usuario, c.resueltoEn = :cuando "
            + "where c.verificacion.id = :verificacionId and c.estado <> :resuelto")
    int cerrarPorAnulacion(@Param("verificacionId") Long verificacionId,
                           @Param("resuelto") EstadoCasoRetiro resuelto,
                           @Param("resolucion") String resolucion,
                           @Param("usuario") com.franco.dev.domain.personas.Usuario usuario,
                           @Param("cuando") java.time.LocalDateTime cuando);

    /**
     * La bandeja, con sus filtros. Todos opcionales.
     *
     * El estado llega como String y se compara contra la columna casteada a texto: es el
     * mismo patrón que usa el filtro de movimientos de caja, para que un bind nulo no rompa.
     * Las fechas van con cast a timestamp por la misma razón.
     */
    @Query("select c from RetiroCaso c where (:estado is null or cast(c.estado as string) = :estado) "
            + "and (:sucursalId is null or c.sucursalId = :sucursalId) "
            + "and (:retiroId is null or c.retiroId = :retiroId) "
            + "and (cast(:desde as timestamp) is null or c.creadoEn >= :desde) "
            + "and (cast(:hasta as timestamp) is null or c.creadoEn <= :hasta) "
            // "Solo los míos": en investigación son los asignados a uno; en resueltos, los que
            // uno cerró. Un único parámetro cubre las dos tabs sin lógica extra del lado del front.
            + "and (:usuarioId is null or c.asignadoA.id = :usuarioId or c.resueltoPor.id = :usuarioId) "
            + "order by c.creadoEn desc")
    Page<RetiroCaso> filter(@Param("estado") String estado,
                            @Param("sucursalId") Long sucursalId,
                            @Param("retiroId") Long retiroId,
                            @Param("desde") java.time.LocalDateTime desde,
                            @Param("hasta") java.time.LocalDateTime hasta,
                            @Param("usuarioId") Long usuarioId,
                            Pageable pageable);

    long countByEstado(EstadoCasoRetiro estado);
}
