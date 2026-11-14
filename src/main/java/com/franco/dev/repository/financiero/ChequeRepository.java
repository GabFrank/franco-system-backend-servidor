package com.franco.dev.repository.financiero;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import javax.persistence.LockModeType;
import java.util.Optional;
import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChequeRepository extends HelperRepository<Cheque, Long> {
    default Class<Cheque> getEntityClass() {
        return Cheque.class;
    }

    @Query("select c from Cheque c " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(CAST(numero as text)) like %?1% or UPPER(orden) like %?1% or UPPER(concepto) like %?1%")
    public List<Cheque> findByAll(String texto);
    
    List<Cheque> findByChequeraId(Long chequeraId);
    
    Cheque findByPagoDetalleCuotaId(Long pagoDetalleCuotaId);

    java.util.List<com.franco.dev.domain.financiero.Cheque> findByEstado(com.franco.dev.domain.financiero.enums.EstadoCheque estado);

    /** Cheques por FECHA DE PAGO en un rango, con filtros opcionales (cuenta, chequera, estado). */
    @Query("select c from Cheque c " +
            "where c.fechaPago between :desde and :hasta " +
            "and (:cuentaId is null or c.cuentaBancaria.id = :cuentaId) " +
            "and (:chequeraId is null or c.chequera.id = :chequeraId) " +
            "and (:estado is null or c.estado = :estado) " +
            "order by c.fechaPago asc")
    List<Cheque> filtrarPorFechaPago(@Param("desde") java.time.LocalDateTime desde,
                                     @Param("hasta") java.time.LocalDateTime hasta,
                                     @Param("cuentaId") Long cuentaId,
                                     @Param("chequeraId") Long chequeraId,
                                     @Param("estado") com.franco.dev.domain.financiero.enums.EstadoCheque estado);

    /**
     * Tablero de cheques (issue #601): por fecha de EMISION ({@code coalesce(fechaEntrega, creadoEn)}), de PAGO o de
     * COBRO; estados como lista (vacía no se puede: quien llama pasa {@code todosLosEstados}); texto contra número,
     * beneficiario y concepto. El tope lo pone el {@code Pageable}.
     */
    @Query("select c from Cheque c " +
            "where (case when :tipoFecha = 'EMISION' then coalesce(c.fechaEntrega, c.creadoEn) " +
            "            when :tipoFecha = 'COBRO' then c.fechaCobro else c.fechaPago end) between :desde and :hasta " +
            "and (:cuentaId is null or c.cuentaBancaria.id = :cuentaId) " +
            "and (:chequeraId is null or c.chequera.id = :chequeraId) " +
            "and (:todosLosEstados = true or c.estado in :estados) " +
            "and (:proveedorId is null or c.proveedor.id = :proveedorId) " +
            "and (:texto is null or str(c.numero) like :texto or upper(c.beneficiario) like :texto " +
            "     or upper(c.concepto) like :texto) " +
            "order by c.id desc")
    List<Cheque> filtrarTablero(@Param("tipoFecha") String tipoFecha,
                                @Param("desde") java.time.LocalDateTime desde,
                                @Param("hasta") java.time.LocalDateTime hasta,
                                @Param("cuentaId") Long cuentaId,
                                @Param("chequeraId") Long chequeraId,
                                @Param("todosLosEstados") boolean todosLosEstados,
                                @Param("estados") java.util.Collection<com.franco.dev.domain.financiero.enums.EstadoCheque> estados,
                                @Param("proveedorId") Long proveedorId,
                                @Param("texto") String texto,
                                org.springframework.data.domain.Pageable tope);

    /** Números con cheque de una chequera desde {@code desde} (un cheque renumerado puede ocupar uno futuro). */
    @Query("select c.numero from Cheque c where c.chequera.id = :chequeraId and c.numero >= :desde")
    List<Double> numerosDesde(@Param("chequeraId") Long chequeraId, @Param("desde") Double desde);

    /** El cheque que tiene un número en una chequera, si hay. */
    java.util.Optional<Cheque> findFirstByChequeraIdAndNumero(Long chequeraId, Double numero);

    /** Los cheques de una emisión en cuotas, por número. */
    List<Cheque> findByLoteIdOrderByNumeroAsc(Long loteId);

    /** Primer y último número emitido por una chequera (anulados incluidos); nulo si no emitió ninguno. */
    @Query("select min(c.numero) from Cheque c where c.chequera.id = :chequeraId")
    Double minNumeroPorChequera(@Param("chequeraId") Long chequeraId);

    @Query("select max(c.numero) from Cheque c where c.chequera.id = :chequeraId")
    Double maxNumeroPorChequera(@Param("chequeraId") Long chequeraId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Cheque e where e.id = :id")
    Optional<Cheque> lockById(@Param("id") Long id);

}
