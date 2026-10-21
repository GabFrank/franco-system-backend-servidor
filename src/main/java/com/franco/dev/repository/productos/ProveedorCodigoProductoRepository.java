package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.ProveedorCodigoProducto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProveedorCodigoProductoRepository extends JpaRepository<ProveedorCodigoProducto, Long> {

    Optional<ProveedorCodigoProducto> findByProveedorIdAndCodigoProveedor(Long proveedorId, String codigoProveedor);
}
