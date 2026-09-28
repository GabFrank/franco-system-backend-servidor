package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.PrecioEspecialSucursal;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PrecioEspecialSucursalRepository extends HelperRepository<PrecioEspecialSucursal, Long> {

    default Class<PrecioEspecialSucursal> getEntityClass() {
        return PrecioEspecialSucursal.class;
    }

    List<PrecioEspecialSucursal> findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(Long precioId, Long sucursalId);

    List<PrecioEspecialSucursal> findByPrecioPorSucursalIdOrderByIdDesc(Long precioId);

    @Query(value = "select e from PrecioEspecialSucursal e " +
            "join e.precioPorSucursal pps join pps.presentacion pr join pr.producto prod " +
            "where (:sucursalId is null or e.sucursal.id = :sucursalId) " +
            "and (:texto is null or UPPER(prod.descripcion) like %:texto%) " +
            "and (:soloVigentes = false or (e.activo = true " +
            "     and (e.fechaDesde is null or e.fechaDesde <= :hoy) " +
            "     and (e.fechaHasta is null or e.fechaHasta >= :hoy))) " +
            "order by e.id desc",
            countQuery = "select count(e) from PrecioEspecialSucursal e " +
                    "join e.precioPorSucursal pps join pps.presentacion pr join pr.producto prod " +
                    "where (:sucursalId is null or e.sucursal.id = :sucursalId) " +
                    "and (:texto is null or UPPER(prod.descripcion) like %:texto%) " +
                    "and (:soloVigentes = false or (e.activo = true " +
                    "     and (e.fechaDesde is null or e.fechaDesde <= :hoy) " +
                    "     and (e.fechaHasta is null or e.fechaHasta >= :hoy)))")
    Page<PrecioEspecialSucursal> filtrar(@Param("sucursalId") Long sucursalId,
                                         @Param("texto") String texto,
                                         @Param("soloVigentes") boolean soloVigentes,
                                         @Param("hoy") LocalDate hoy,
                                         Pageable pageable);
}
