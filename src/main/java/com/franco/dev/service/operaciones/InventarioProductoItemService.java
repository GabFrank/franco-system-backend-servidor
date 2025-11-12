package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.repository.operaciones.InventarioProductoItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class InventarioProductoItemService
        extends CrudService<InventarioProductoItem, InventarioProductoItemRepository, Long> {

    private final InventarioProductoItemRepository repository;

    @Override
    public InventarioProductoItemRepository getRepository() {
        return repository;
    }

    public List<InventarioProductoItem> findByInventarioProductoId(Long id, Pageable pageable) {
        return repository.findByInventarioProductoIdOrderByIdDesc(id, pageable);
    }

    public List<InventarioProductoItem> findByInventarioProductoId(Long id) {
        return repository.findByInventarioProductoId(id);
    }

    public List<InventarioProductoItem> findByInventarioProductoIdAndPresentacionId(Long inventarioProductoId,
            Long presentacionId, Pageable pageable) {
        return repository.findByInventarioProductoIdAndPresentacionIdOrderByVencimientoDesc(inventarioProductoId,
                presentacionId, pageable);
    }

    public List<InventarioProductoItem> findItemsDeInventariosAnteriores(Long presentacionId, Long sucursalId,
            Long sectorId, Long zonaId, LocalDateTime fechaInicioInventarioActual, Pageable pageable) {
        List<InventarioProductoItem> result = repository.findItemsDeInventariosAnteriores(presentacionId, sucursalId,
                sectorId, zonaId, fechaInicioInventarioActual, pageable);
        if (result.isEmpty() && pageable.getPageNumber() == 0) {
            result = repository.findItemsDeInventariosAnterioresSoloSucursal(presentacionId, sucursalId,
                    fechaInicioInventarioActual, pageable);
        }
        return result;
    }

    public List<InventarioProductoItem> findByInventarioIdAndProductoId(Long inventarioId, Long productoId) {
        return repository.findByInventarioIdAndProductoId(inventarioId, productoId);
    }

    public Page<InventarioProductoItem> findItemsParaRevisar(Long inventarioId, String filtro, Pageable pageable) {
        return repository.findItemsParaRevisar(inventarioId, filtro, pageable);
    }

    public Page<InventarioProductoItem> findAllWithFilters(
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            LocalDateTime startDate,
            LocalDateTime endDate,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            Pageable pageable) {
        return repository.findAllWithFilters(
                sucursalIdList, sectorIdList, zonaIdList, startDate, endDate, usuarioIdList, productoIdList, pageable);
    }

    public Page<InventarioProductoItem> findProductosVencidos(
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            Pageable pageable) {
        return repository.findProductosVencidos(
                sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList, pageable);
    }

    public Page<InventarioProductoItem> findProductosVencidosConFecha(
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            LocalDateTime startDate,
            LocalDateTime endDate,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            Pageable pageable) {
        return repository.findProductosVencidosConFecha(
                sucursalIdList, sectorIdList, zonaIdList, startDate, endDate, usuarioIdList, productoIdList, pageable);
    }

    public Page<InventarioProductoItem> findProductosProximosAVencer(
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            LocalDateTime fechaProximoVencimiento,
            Pageable pageable) {
        return repository.findProductosProximosAVencer(
                sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList, fechaProximoVencimiento,
                pageable);
    }

    public Page<InventarioProductoItem> findProductosVencidosCompleto(
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable LocalDateTime startDate,
            @Nullable LocalDateTime endDate,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            @Nullable Boolean incluirProximosVencer,
            @Nullable Integer diasProximosVencer,
            @Nullable Boolean soloRealmenteVencidos,
            Pageable pageable) {

        if (startDate != null && endDate != null) {
            return findProductosVencidosConFecha(
                    sucursalIdList, sectorIdList, zonaIdList, startDate, endDate, usuarioIdList, productoIdList,
                    pageable);
        }

        if (Boolean.TRUE.equals(incluirProximosVencer) && diasProximosVencer != null) {
            LocalDateTime fechaLimite = LocalDateTime.now().plusDays(diasProximosVencer);
            return findProductosProximosAVencer(
                    sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList, fechaLimite, pageable);
        }

        return findProductosVencidos(
                sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList, pageable);
    }

    public Page<InventarioProductoItem> findProductosVencidosPorSucursal(
            @Nullable Long sucursalId,
            Pageable pageable) {
        List<Long> sucursalIdList = sucursalId != null ? Collections.singletonList(sucursalId) : null;
        return findProductosVencidos(sucursalIdList, null, null, null, null, pageable);
    }

    public Page<InventarioProductoItem> findProductosVencidosPorSector(
            @Nullable Long sectorId,
            Pageable pageable) {
        List<Long> sectorIdList = sectorId != null ? Collections.singletonList(sectorId) : null;
        return findProductosVencidos(null, sectorIdList, null, null, null, pageable);
    }

    public Page<InventarioProductoItem> findProductosVencidosPorZona(
            @Nullable Long zonaId,
            Pageable pageable) {
        List<Long> zonaIdList = zonaId != null ? Collections.singletonList(zonaId) : null;
        return findProductosVencidos(null, null, zonaIdList, null, null, pageable);
    }

    public Page<InventarioProductoItem> findProductosVencidosPorSucursalYSector(
            @Nullable Long sucursalId,
            @Nullable Long sectorId,
            Pageable pageable) {
        List<Long> sucursalIdList = sucursalId != null ? Collections.singletonList(sucursalId) : null;
        List<Long> sectorIdList = sectorId != null ? Collections.singletonList(sectorId) : null;
        return findProductosVencidos(sucursalIdList, sectorIdList, null, null, null, pageable);
    }

    public Long countProductosVencidos(
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList) {
        return repository.countProductosVencidos(
                sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList);
    }
    @Override
    public InventarioProductoItem save(InventarioProductoItem entity) {
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());

        Long inventarioId = null;
        Long productoId = null;
        if (entity.getInventarioProducto() != null && entity.getInventarioProducto().getInventario() != null) {
            inventarioId = entity.getInventarioProducto().getInventario().getId();
        }
        if (entity.getPresentacion() != null && entity.getPresentacion().getProducto() != null) {
            productoId = entity.getPresentacion().getProducto().getId();
        }

        if (inventarioId != null && productoId != null) {
            List<InventarioProductoItem> existingItems = findByInventarioIdAndProductoId(inventarioId, productoId);
            if (!existingItems.isEmpty()) {
                throw new IllegalStateException("El producto ya fue registrado en este inventario");
            }
        }

        return super.save(entity);
    }
}