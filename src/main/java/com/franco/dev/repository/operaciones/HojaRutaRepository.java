package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.HojaRuta;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HojaRutaRepository extends HelperRepository<HojaRuta, Long> {

    default Class<HojaRuta> getEntityClass() {
        return HojaRuta.class;
    }

    Page<HojaRuta> findByVehiculoId(Long vehiculoId, Pageable pageable);

    Page<HojaRuta> findByChoferId(Long choferId, Pageable pageable);

    // Devuelve lista y no Optional: un vehiculo puede acumular varias hojas en estado
    // EN_RUTA y un Optional lanzaria NonUniqueResultException. El service toma la mas reciente.
    @Query("SELECT h FROM HojaRuta h WHERE h.vehiculo.id = :vehiculoId AND h.estado = 'EN_RUTA' ORDER BY h.id DESC")
    List<HojaRuta> findActivasByVehiculoId(Long vehiculoId);

    @Query("SELECT h FROM HojaRuta h WHERE h.chofer IS NOT NULL ORDER BY h.id DESC")
    Page<HojaRuta> findHojasRutaConEntregas(Pageable pageable);

    List<HojaRuta> findByFechaSalidaBetweenOrderByIdDesc(LocalDateTime inicio, LocalDateTime fin);

    /**
     * Pagina las hojas de ruta por rango de salida y texto libre (chofer, chapa o modelo).
     * Trae en la misma consulta las relaciones to-one que la lista muestra, para no
     * resolverlas de a una por fila.
     */
    @Query(value = "SELECT h FROM HojaRuta h " +
            "LEFT JOIN FETCH h.vehiculo v " +
            "LEFT JOIN FETCH v.modelo mo " +
            "LEFT JOIN FETCH mo.marca " +
            "LEFT JOIN FETCH h.chofer c " +
            "WHERE h.fechaSalida BETWEEN :inicio AND :fin " +
            "AND (:texto IS NULL " +
            "  OR UPPER(c.nombre) LIKE :texto " +
            "  OR UPPER(v.chapa) LIKE :texto " +
            "  OR UPPER(mo.descripcion) LIKE :texto)", countQuery = "SELECT COUNT(h) FROM HojaRuta h " +
                    "LEFT JOIN h.vehiculo v " +
                    "LEFT JOIN v.modelo mo " +
                    "LEFT JOIN h.chofer c " +
                    "WHERE h.fechaSalida BETWEEN :inicio AND :fin " +
                    "AND (:texto IS NULL " +
                    "  OR UPPER(c.nombre) LIKE :texto " +
                    "  OR UPPER(v.chapa) LIKE :texto " +
                    "  OR UPPER(mo.descripcion) LIKE :texto)")
    Page<HojaRuta> buscarPorFecha(@Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("texto") String texto,
            Pageable pageable);

    /**
     * Inicializa los acompaniantes de las hojas ya cargadas en una sola consulta. No se
     * hace con JOIN FETCH en {@link #buscarPorFecha} porque paginar junto a una coleccion
     * obliga a Hibernate a traer todo y paginar en memoria.
     */
    @Query("SELECT DISTINCT h FROM HojaRuta h LEFT JOIN FETCH h.acompanantes WHERE h.id IN :ids")
    List<HojaRuta> fetchAcompanantes(@Param("ids") List<Long> ids);
}
