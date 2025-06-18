package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.PromocionPorSucursal;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PromocionPorSucursalRepository extends HelperRepository<PromocionPorSucursal, Long> {

    default Class<PromocionPorSucursal> getEntityClass() {
        return PromocionPorSucursal.class;
    }

    List<PromocionPorSucursal> findBySucursalId(Long sucursalId);

    List<PromocionPorSucursal> findByPrecioId(Long precioId);

    List<PromocionPorSucursal> findBySucursalIdAndActivoTrue(Long sucursalId);

    @Query(value = "SELECT ps.* FROM productos.promocion_por_sucursal ps " +
            "INNER JOIN productos.precio_por_sucursal p ON ps.precio_sucursal_id = p.id " +
            "WHERE p.presentacion_id = ?1", nativeQuery = true)
    List<PromocionPorSucursal> findByPresentacionId(Long presentacionId);

    @Query(value = "SELECT ps.* FROM productos.promocion_por_sucursal ps " +
            "INNER JOIN productos.precio_por_sucursal p ON ps.precio_sucursal_id = p.id " +
            "WHERE p.presentacion_id = ?1 AND ps.activo = true", nativeQuery = true)
    List<PromocionPorSucursal> findActiveByPresentacionId(Long presentacionId);

    List<PromocionPorSucursal> findByTipoPromocion(String tipoPromocion);

    List<PromocionPorSucursal> findBySucursalIdAndTipoPromocionAndActivoTrue(Long sucursalId, String tipoPromocion);

} 