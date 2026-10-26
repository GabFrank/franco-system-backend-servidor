package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.ProveedorCodigoProducto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProveedorCodigoProductoRepository extends JpaRepository<ProveedorCodigoProducto, Long> {

    Optional<ProveedorCodigoProducto> findByProveedorIdAndCodigoProveedor(Long proveedorId, String codigoProveedor);

    Optional<ProveedorCodigoProducto> findByProveedorIdAndCodigoProveedorIsNullAndDescripcionNormalizada(
            Long proveedorId, String descripcionNormalizada);

    /** Todo lo aprendido con esa descripción, venga de un renglón con código o sin código. */
    List<ProveedorCodigoProducto> findByProveedorIdAndDescripcionNormalizada(Long proveedorId, String descripcionNormalizada);
}
