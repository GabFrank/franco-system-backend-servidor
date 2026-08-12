package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.enums.InventarioProductoEstado;
import com.franco.dev.repository.operaciones.InventarioProductoItemRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuraciones.ModificacionService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class InventarioProductoItemService
        extends CrudService<InventarioProductoItem, InventarioProductoItemRepository, Long> {

    private static final int DIAS_POR_VENCER_DEFAULT = 30;
    /** Bordes del rango de vencimiento cuando el usuario deja un extremo vacio. */
    private static final LocalDateTime RANGO_VENCIMIENTO_MIN = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime RANGO_VENCIMIENTO_MAX = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final InventarioProductoItemRepository repository;

    @Autowired
    private ModificacionService modificacionService;

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
            @Nullable String estado,
            @Nullable String vencimientoFiltro,
            @Nullable Integer diasPorVencer,
            @Nullable LocalDateTime vencimientoDesde,
            @Nullable LocalDateTime vencimientoHasta,
            Pageable pageable) {

        LocalDateTime ahora = LocalDateTime.now();
        int dias = diasPorVencer != null && diasPorVencer > 0 ? diasPorVencer : DIAS_POR_VENCER_DEFAULT;
        boolean filtrarRango = vencimientoDesde != null || vencimientoHasta != null;

        return repository.findAllWithFilters(
                toLongList(sucursalIdList),
                toLongList(sectorIdList),
                toLongList(zonaIdList),
                startDate,
                endDate,
                toLongList(usuarioIdList),
                toLongList(productoIdList),
                estado,
                normalizarVencimientoFiltro(vencimientoFiltro),
                ahora,
                ahora.plusDays(dias),
                filtrarRango,
                vencimientoDesde != null ? vencimientoDesde : RANGO_VENCIMIENTO_MIN,
                vencimientoHasta != null ? vencimientoHasta : RANGO_VENCIMIENTO_MAX,
                InventarioProductoEstado.VENCIDO,
                pageable);
    }

    /**
     * "TODOS" y el vacio son lo mismo que no filtrar: se normalizan a null para que
     * la query saltee el predicado.
     */
    @Nullable
    private String normalizarVencimientoFiltro(@Nullable String vencimientoFiltro) {
        if (vencimientoFiltro == null || vencimientoFiltro.trim().isEmpty()
                || "TODOS".equalsIgnoreCase(vencimientoFiltro.trim())) {
            return null;
        }
        return vencimientoFiltro.trim().toUpperCase();
    }

    public Page<InventarioProductoItem> findProductosVencidos(
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            Pageable pageable) {
        return repository.findProductosVencidos(
                toLongList(sucursalIdList),
                toLongList(sectorIdList),
                toLongList(zonaIdList),
                toLongList(usuarioIdList),
                toLongList(productoIdList),
                pageable);
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
                toLongList(sucursalIdList),
                toLongList(sectorIdList),
                toLongList(zonaIdList),
                startDate,
                endDate,
                toLongList(usuarioIdList),
                toLongList(productoIdList),
                pageable);
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
                toLongList(sucursalIdList),
                toLongList(sectorIdList),
                toLongList(zonaIdList),
                toLongList(usuarioIdList),
                toLongList(productoIdList),
                fechaProximoVencimiento,
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
                toLongList(sucursalIdList),
                toLongList(sectorIdList),
                toLongList(zonaIdList),
                toLongList(usuarioIdList),
                toLongList(productoIdList));
    }

    @Override
    public InventarioProductoItem save(InventarioProductoItem entity) {
        if (entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());

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
            for (InventarioProductoItem item : existingItems) {
                if (!Objects.equals(item.getId(), entity.getId())
                        && Objects.equals(item.getVencimiento(), entity.getVencimiento())) {
                    throw new IllegalStateException(
                            "El producto ya fue registrado en este inventario con el mismo vencimiento");
                }
            }
        }

        InventarioProductoItem entidadAnterior = null;
        boolean esNuevo = (entity.getId() == null);
        if (!esNuevo) {
            entidadAnterior = repository.findById(entity.getId()).orElse(null);
        }

        InventarioProductoItem e = super.save(entity);
        repository.flush();
        try {
            if (esNuevo) {
                modificacionService.registrarInsercion(e, "INVENTARIO_ITEM", "operaciones", "inventario_producto_item");
            } else if (entidadAnterior != null) {
                modificacionService.registrarActualizacion(entidadAnterior, e, "INVENTARIO_ITEM", "operaciones",
                        "inventario_producto_item");
            }
        } catch (Exception ex) {
            System.err.println("Error registrando auditoría de inventario item: " + ex.getMessage());
        }

        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        try {
            InventarioProductoItem entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                Boolean resultado = super.deleteById(id);
                try {
                    modificacionService.registrarEliminacion(entidad, "INVENTARIO_ITEM", "operaciones",
                            "inventario_producto_item");
                } catch (Exception ex) {
                    System.err.println("Error registrando eliminación de inventario item: " + ex.getMessage());
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * GraphQL puede enviar [Int] como Integer; Hibernate requiere Long en listas IN.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private List<Long> toLongList(@Nullable List<Long> idList) {
        if (idList == null || idList.isEmpty()) {
            return null;
        }
        List<Long> converted = ((List<?>) (List<?>) idList).stream()
                .filter(Objects::nonNull)
                .map(id -> id instanceof Number ? ((Number) id).longValue() : Long.parseLong(id.toString()))
                .collect(Collectors.toList());
        return converted.isEmpty() ? null : converted;
    }
}