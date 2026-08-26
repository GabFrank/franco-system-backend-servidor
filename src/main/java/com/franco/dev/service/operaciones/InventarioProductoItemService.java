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

    /**
     * Guarda un item de conteo, rechazando el renglon duplicado.
     *
     * Usado en:
     * - Desktop: Si (modulo de inventario, dialogo de agregar producto y edicion del conteo)
     * - Mobile: Si (carga del conteo: agregar producto y guardar cantidades)
     *
     * <p>
     * Un duplicado es <b>el mismo renglon dos veces en la misma zona</b>:
     * misma zona, misma presentacion y mismo vencimiento. Ese es el unico caso
     * que produce un dato sin sentido, porque
     * {@code finalizarInventarioEnSucursal()} suma los dos renglones y el
     * conteo sale doble.
     *
     * <p>
     * <b>La clave era {@code (inventario, producto, vencimiento)} y estaba
     * demasiado abierta en los tres ejes.</b> Rechazaba tres cosas legitimas:
     *
     * <ol>
     * <li><b>El mismo producto en dos zonas.</b> Es el caso normal de un
     * inventario por zona: hay stock en gondola y en deposito, y los conteos se
     * suman. Con el alcance en todo el inventario, contar el segundo fallaba.</li>
     * <li><b>Unidad y caja x12 del mismo producto.</b> Son dos presentaciones y
     * dos renglones legitimos, pero para una clave por producto eran el mismo.</li>
     * <li><b>Dos renglones sin vencimiento.</b> {@code Objects.equals} toma dos
     * nulos por iguales, y el vencimiento es opcional en los dos frentes, asi
     * que agregar un segundo producto sin fecha a una toma siempre fallaba.</li>
     * </ol>
     *
     * <p>
     * <b>El cambio es una relajacion, no un cambio de contrato.</b> Todo lo que
     * la clave nueva rechaza ya lo rechazaba la anterior —una zona esta dentro
     * de su inventario y una presentacion pertenece a su producto—, asi que
     * ningun flujo que hoy funcione deja de funcionar. La firma, el input de
     * GraphQL y el schema quedan igual.
     *
     * <p>
     * El mensaje se arma listo para mostrar: el cliente es capa de presentacion
     * y no tiene que interpretar el error ni reimplementar esta regla.
     */
    @Override
    public InventarioProductoItem save(InventarioProductoItem entity) {
        if (entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());

        verificarRenglonDuplicado(entity);

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

    /**
     * Rechaza el mismo renglon repetido dentro de una zona.
     *
     * <p>
     * Sin zona o sin presentacion no se puede decidir, y se deja pasar: es lo
     * que hacia la version anterior cuando no podia resolver el inventario o el
     * producto, y cambiarlo ahora rechazaria altas que hoy entran.
     */
    private void verificarRenglonDuplicado(InventarioProductoItem entity) {
        Long inventarioProductoId = entity.getInventarioProducto() != null
                ? entity.getInventarioProducto().getId()
                : null;
        Long presentacionId = entity.getPresentacion() != null ? entity.getPresentacion().getId() : null;

        if (inventarioProductoId == null || presentacionId == null) {
            return;
        }

        for (InventarioProductoItem item : findByInventarioProductoId(inventarioProductoId)) {
            boolean esOtroRenglon = !Objects.equals(item.getId(), entity.getId());
            boolean mismaPresentacion = item.getPresentacion() != null
                    && Objects.equals(item.getPresentacion().getId(), presentacionId);
            // Dos vencimientos nulos son iguales a proposito: dos renglones de
            // la misma presentacion sin fecha son el mismo renglon dos veces.
            boolean mismoVencimiento = Objects.equals(item.getVencimiento(), entity.getVencimiento());

            if (esOtroRenglon && mismaPresentacion && mismoVencimiento) {
                throw new IllegalStateException(mensajeDeRenglonDuplicado(entity));
            }
        }
    }

    /**
     * El texto que ve el operador. Se arma aca —y no en cada cliente— para que
     * el escritorio y el telefono digan lo mismo, y para que el frontend siga
     * siendo capa de presentacion.
     */
    private String mensajeDeRenglonDuplicado(InventarioProductoItem entity) {
        String zona = null;
        if (entity.getInventarioProducto() != null && entity.getInventarioProducto().getZona() != null) {
            zona = entity.getInventarioProducto().getZona().getDescripcion();
        }
        String donde = (zona != null && !zona.trim().isEmpty()) ? "la zona " + zona.trim() : "esta zona";

        if (entity.getVencimiento() == null) {
            return "Esa presentacion ya esta en " + donde
                    + " sin vencimiento. Carguele la fecha a la que ya esta, o conte las dos juntas en ese renglon.";
        }
        return "Esa presentacion ya esta en " + donde + " con el mismo vencimiento. "
                + "Un lote distinto va con otra fecha; el mismo lote se cuenta en un solo renglon.";
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