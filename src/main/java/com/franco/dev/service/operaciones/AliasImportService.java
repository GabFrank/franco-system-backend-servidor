package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.AliasProductoImport;
import com.franco.dev.domain.operaciones.AliasProveedorImport;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.AliasProductoImportRepository;
import com.franco.dev.repository.operaciones.AliasProveedorImportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Gestion de aliases aprendidos durante el flujo de importacion de facturas.
 * Inserts idempotentes: si el alias ya existe se ignora silenciosamente (no duplica).
 */
@Service
public class AliasImportService {

    private static final Logger log = LoggerFactory.getLogger(AliasImportService.class);

    @Autowired
    private AliasProveedorImportRepository proveedorRepo;

    @Autowired
    private AliasProductoImportRepository productoRepo;

    public List<AliasProveedorImport> findProveedorByRuc(String ruc) {
        if (ruc == null || ruc.isEmpty()) return List.of();
        return proveedorRepo.findByRuc(ruc);
    }

    public Optional<AliasProveedorImport> findProveedorByTextoOcr(String texto) {
        if (texto == null || texto.isEmpty()) return Optional.empty();
        return proveedorRepo.findByTextoOcr(texto);
    }

    public List<AliasProductoImport> findProductoByCodigoOcr(String codigo) {
        if (codigo == null || codigo.isEmpty()) return List.of();
        return productoRepo.findByCodigoOcr(codigo);
    }

    public Optional<AliasProductoImport> findProductoByTextoYProveedor(String texto, Long proveedorId) {
        if (texto == null || texto.isEmpty() || proveedorId == null) return Optional.empty();
        return productoRepo.findByTextoOcrAndProveedorId(texto, proveedorId);
    }

    /** Insert idempotente (texto_ocr es unique). */
    @Transactional
    public AliasProveedorImport guardarAliasProveedor(String textoOcr, String ruc, Proveedor proveedor) {
        if (textoOcr == null || textoOcr.isEmpty() || proveedor == null) return null;
        Optional<AliasProveedorImport> existente = proveedorRepo.findByTextoOcr(textoOcr);
        if (existente.isPresent()) {
            log.debug("Alias proveedor ya existe para texto='{}', skip", textoOcr);
            return existente.get();
        }
        AliasProveedorImport a = new AliasProveedorImport();
        a.setTextoOcr(textoOcr);
        a.setRuc(ruc);
        a.setProveedor(proveedor);
        return proveedorRepo.save(a);
    }

    /** Insert idempotente (texto_ocr + proveedor_id es unique). */
    @Transactional
    public AliasProductoImport guardarAliasProducto(String textoOcr, String codigoOcr,
                                                     Producto producto, Proveedor proveedor) {
        if (textoOcr == null || textoOcr.isEmpty() || producto == null || proveedor == null) return null;
        Optional<AliasProductoImport> existente =
                productoRepo.findByTextoOcrAndProveedorId(textoOcr, proveedor.getId());
        if (existente.isPresent()) {
            log.debug("Alias producto ya existe para texto='{}' proveedor={}, skip", textoOcr, proveedor.getId());
            return existente.get();
        }
        AliasProductoImport a = new AliasProductoImport();
        a.setTextoOcr(textoOcr);
        a.setCodigoOcr(codigoOcr);
        a.setProducto(producto);
        a.setProveedor(proveedor);
        return productoRepo.save(a);
    }
}
