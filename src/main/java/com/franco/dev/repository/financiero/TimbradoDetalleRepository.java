package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.repository.HelperRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface TimbradoDetalleRepository extends HelperRepository<TimbradoDetalle, Long> {

    default Class<TimbradoDetalle> getEntityClass() {
        return TimbradoDetalle.class;
    }
    public List<TimbradoDetalle> findByTimbradoId(Long id);

    /**
     * Toma la fila del timbrado con lock pesimista. Es lo que serializa la asignacion del numero
     * de una nota (remision o credito): dos emisiones simultaneas tomarian el mismo MAX+1.
     */
    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TimbradoDetalle t WHERE t.id = :id")
    Optional<TimbradoDetalle> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    
    @Query(value = "SELECT * FROM financiero.timbrado_detalle td WHERE td.timbrado_id = ?1 ORDER BY td.id ASC", nativeQuery = true)
    public Page<TimbradoDetalle> findByTimbradoId(Long timbradoId, Pageable pageable);

    // findByIdAndSucursalId
    public Optional<TimbradoDetalle> findByIdAndSucursalId(Long id, Long sucId);

    // deleteByIdAndSucursalId
    public Boolean deleteByIdAndSucursalId(Long id, Long sucId);

    List<TimbradoDetalle> findByPuntoDeVentaId(Long id);

    List<TimbradoDetalle> findBySucursalId(Long id);

    /**
     * Las filas del timbrado como columnas sueltas: {id, sucursal_id, punto_expedicion, activo}.
     *
     * Nativa y sin entidad a proposito. La PK real es (id, sucursal_id) pero la entidad mapea solo
     * `id`: con un id compartido entre sucursales (105, 89, 93) Hibernate devuelve una sola
     * instancia para las dos filas, con la sucursal de la que cargo primero. Para calcular series
     * hace falta cada fila tal como esta en la base.
     */
    @Query(value = "SELECT td.id, td.sucursal_id, td.punto_expedicion, td.activo "
            + "FROM financiero.timbrado_detalle td WHERE td.timbrado_id = :timbradoId", nativeQuery = true)
    List<Object[]> findFilasDeSerieByTimbradoId(
            @org.springframework.data.repository.query.Param("timbradoId") Long timbradoId);
}