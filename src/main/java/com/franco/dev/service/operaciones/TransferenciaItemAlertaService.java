package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.InventarioProductoEstado;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.graphql.operaciones.dto.InventarioAlertaProjectionDTO;
import com.franco.dev.graphql.operaciones.dto.ProductoVencimientoCompraProjectionDTO;
import com.franco.dev.graphql.operaciones.dto.TransferenciaItemAlertaDTO;
import com.franco.dev.repository.operaciones.InventarioProductoItemRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionItemRepository;
import com.franco.dev.repository.operaciones.TransferenciaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferenciaItemAlertaService {

    private final TransferenciaService transferenciaService;
    private final TransferenciaItemRepository transferenciaItemRepository;
    private final InventarioProductoItemRepository inventarioProductoItemRepository;
    private final NotaRecepcionItemRepository notaRecepcionItemRepository;

    @Transactional(readOnly = true)
    public List<TransferenciaItemAlertaDTO> calcularAlertas(Long transferenciaId, List<Long> itemIds) {
        if (transferenciaId == null || itemIds == null || itemIds.isEmpty()) {
            return Collections.emptyList();
        }

        Transferencia transferencia = transferenciaService.findById(transferenciaId).orElse(null);
        if (transferencia == null
                || transferencia.getSucursalOrigen() == null
                || transferencia.getSucursalOrigen().getId() == null) {
            return Collections.emptyList();
        }

        List<TransferenciaItem> items = transferenciaItemRepository
                .findByIdInAndTransferenciaId(itemIds, transferenciaId);
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        Long sucursalOrigenId = transferencia.getSucursalOrigen().getId();

        List<Long> productoIds = items.stream()
                .map(TransferenciaItem::getPresentacionPreTransferencia)
                .filter(Objects::nonNull)
                .map(Presentacion::getProducto)
                .filter(Objects::nonNull)
                .map(producto -> producto.getId())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, List<InventarioAlertaProjectionDTO>> inventarioPorProducto =
                cargarInventarioAlertas(sucursalOrigenId, productoIds);
        Map<Long, LocalDate> compraPorProducto = cargarVencimientoCompra(productoIds);

        LocalDate hoy = LocalDate.now();
        List<TransferenciaItemAlertaDTO> resultado = new ArrayList<>();

        for (TransferenciaItem item : items) {
            resultado.add(construirAlerta(item, inventarioPorProducto, compraPorProducto, hoy));
        }

        return resultado;
    }

    private TransferenciaItemAlertaDTO construirAlerta(
            TransferenciaItem item,
            Map<Long, List<InventarioAlertaProjectionDTO>> inventarioPorProducto,
            Map<Long, LocalDate> compraPorProducto,
            LocalDate hoy) {
        TransferenciaItemAlertaDTO dto = new TransferenciaItemAlertaDTO();
        dto.setTransferenciaItemId(item.getId());
        dto.setAlertaVencido(false);
        dto.setAlertaAveriado(false);
        dto.setFechaVencimientoReferencia(null);

        Presentacion presentacion = item.getPresentacionPreTransferencia();
        if (presentacion == null || presentacion.getProducto() == null) {
            return dto;
        }

        Long presentacionId = presentacion.getId();
        Long productoId = presentacion.getProducto().getId();

        InventarioAlertaProjectionDTO inventario = productoId != null
                ? resolverInventarioProducto(productoId, presentacionId, inventarioPorProducto)
                : null;

        boolean averiado = tieneEstadoInventario(inventarioPorProducto.get(productoId), InventarioProductoEstado.AVERIADO)
                || (inventario != null && InventarioProductoEstado.AVERIADO == inventario.getEstado());

        LocalDate fechaReferencia = resolverFechaReferencia(
                item,
                inventario,
                productoId != null ? compraPorProducto.get(productoId) : null);

        boolean vencido = tieneEstadoInventario(inventarioPorProducto.get(productoId), InventarioProductoEstado.VENCIDO)
                || (inventario != null && InventarioProductoEstado.VENCIDO == inventario.getEstado())
                || (fechaReferencia != null && fechaReferencia.isBefore(hoy));

        dto.setAlertaAveriado(averiado);
        dto.setAlertaVencido(vencido && !averiado);
        if (fechaReferencia != null) {
            dto.setFechaVencimientoReferencia(fechaReferencia.atStartOfDay());
        }

        return dto;
    }

    private InventarioAlertaProjectionDTO resolverInventarioProducto(
            Long productoId,
            Long presentacionId,
            Map<Long, List<InventarioAlertaProjectionDTO>> inventarioPorProducto) {
        List<InventarioAlertaProjectionDTO> registros = inventarioPorProducto.get(productoId);
        if (registros == null || registros.isEmpty()) {
            return null;
        }

        if (presentacionId != null) {
            for (InventarioAlertaProjectionDTO registro : registros) {
                if (presentacionId.equals(registro.getPresentacionId())) {
                    return registro;
                }
            }
        }

        return consolidarRegistrosInventario(productoId, registros);
    }

    private InventarioAlertaProjectionDTO consolidarRegistrosInventario(
            Long productoId,
            List<InventarioAlertaProjectionDTO> registros) {
        InventarioAlertaProjectionDTO consolidado = new InventarioAlertaProjectionDTO();
        consolidado.setProductoId(productoId);

        boolean averiado = tieneEstadoInventario(registros, InventarioProductoEstado.AVERIADO);
        boolean vencido = tieneEstadoInventario(registros, InventarioProductoEstado.VENCIDO);

        if (averiado) {
            consolidado.setEstado(InventarioProductoEstado.AVERIADO);
        } else if (vencido) {
            consolidado.setEstado(InventarioProductoEstado.VENCIDO);
        } else {
            consolidado.setEstado(InventarioProductoEstado.BUENO);
        }

        consolidado.setVencimiento(registros.stream()
                .map(InventarioAlertaProjectionDTO::getVencimiento)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null));

        return consolidado;
    }

    private boolean tieneEstadoInventario(
            List<InventarioAlertaProjectionDTO> registros,
            InventarioProductoEstado estado) {
        if (registros == null || registros.isEmpty()) {
            return false;
        }
        return registros.stream().anyMatch(registro -> estado == registro.getEstado());
    }

    private Map<Long, List<InventarioAlertaProjectionDTO>> cargarInventarioAlertas(
            Long sucursalOrigenId,
            List<Long> productoIds) {
        if (productoIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<InventarioAlertaProjectionDTO>> resultado = new HashMap<>();
        for (InventarioAlertaProjectionDTO dto : inventarioProductoItemRepository
                .findAlertasBySucursalAndProductoIds(sucursalOrigenId, productoIds)) {
            resultado.computeIfAbsent(dto.getProductoId(), key -> new ArrayList<>()).add(dto);
        }
        return resultado;
    }

    private Map<Long, LocalDate> cargarVencimientoCompra(List<Long> productoIds) {
        if (productoIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, LocalDate> resultado = new HashMap<>();
        for (ProductoVencimientoCompraProjectionDTO dto : notaRecepcionItemRepository
                .findUltimoVencimientoEnNotaByProductoIds(productoIds)) {
            resultado.put(dto.getProductoId(), dto.getVencimientoEnNota());
        }
        return resultado;
    }

    private LocalDate resolverFechaReferencia(
            TransferenciaItem item,
            InventarioAlertaProjectionDTO inventario,
            LocalDate vencimientoCompra) {
        LocalDate itemVencimiento = toLocalDate(primerVencimientoItem(item));
        if (itemVencimiento != null) {
            return itemVencimiento;
        }

        if (inventario != null && inventario.getVencimiento() != null) {
            return inventario.getVencimiento().toLocalDate();
        }

        return vencimientoCompra;
    }

    private LocalDateTime primerVencimientoItem(TransferenciaItem item) {
        if (item.getVencimientoPreTransferencia() != null) {
            return item.getVencimientoPreTransferencia();
        }
        if (item.getVencimientoPreparacion() != null) {
            return item.getVencimientoPreparacion();
        }
        if (item.getVencimientoTransporte() != null) {
            return item.getVencimientoTransporte();
        }
        return item.getVencimientoRecepcion();
    }

    private LocalDate toLocalDate(LocalDateTime value) {
        return value != null ? value.toLocalDate() : null;
    }
}
