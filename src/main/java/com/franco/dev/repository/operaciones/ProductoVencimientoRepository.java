package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.ProductoVencimiento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoVencimientoRepository extends HelperRepository<ProductoVencimiento, Long> {

    default Class<ProductoVencimiento> getEntityClass() {
        return ProductoVencimiento.class;
    }

    @Query("SELECT pv FROM ProductoVencimiento pv " +
           "WHERE pv.producto.id = :productoId AND pv.sucursal.id = :sucursalId " +
           "ORDER BY pv.fechaVencimiento ASC")
    List<ProductoVencimiento> findByProductoIdAndSucursalId(@Param("productoId") Long productoId,
                                                            @Param("sucursalId") Long sucursalId);
}

