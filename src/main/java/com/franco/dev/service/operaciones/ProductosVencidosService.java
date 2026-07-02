package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.enums.FuenteVerdadVencimiento;
import com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProductosVencidosService {

    private static final String COLOR_SUCCESS = "#4caf50";
    private static final String COLOR_WARNING = "#ff9800";
    private static final String COLOR_DANGER = "#f44336";
    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<ProductoVencidoViewDTO> buscarProductosVencidos(
            @Nullable LocalDateTime startDate,
            @Nullable LocalDateTime endDate,
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            @Nullable List<String> fuenteVerdadList,
            @Nullable Boolean soloRealmenteVencidos,
            Pageable pageable) {

        FiltrosProductosVencidos filtros = new FiltrosProductosVencidos(
                startDate, endDate, sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList,
                fuenteVerdadList, soloRealmenteVencidos);

        String sqlBase = construirSqlBase(filtros);
        Map<String, Object> params = filtros.parametros();

        String countSql = "SELECT COUNT(*) FROM (" + sqlBase + ") AS total";
        Query countQuery = entityManager.createNativeQuery(countSql);
        aplicarParametros(countQuery, params);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        String paginatedSql = sqlBase + " ORDER BY vencimiento DESC LIMIT :pageSize OFFSET :pageOffset";
        Query dataQuery = entityManager.createNativeQuery(paginatedSql);
        aplicarParametros(dataQuery, params);
        dataQuery.setParameter("pageSize", pageable.getPageSize());
        dataQuery.setParameter("pageOffset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        List<ProductoVencidoViewDTO> content = new ArrayList<>();
        for (Object[] row : rows) {
            content.add(enriquecer(mapearFila(row)));
        }

        return new PageImpl<>(content, pageable, total);
    }

    private String construirSqlBase(FiltrosProductosVencidos filtros) {
        return ""
                + "WITH ultimo_inv AS ( "
                + "  SELECT inv.sucursal_id, MAX(inv.id) AS inventario_id, MAX(inv.fecha_inicio) AS fecha_inicio "
                + "  FROM operaciones.inventario inv "
                + "  GROUP BY inv.sucursal_id "
                + "), "
                + "inv_snap AS ( "
                + "  SELECT inv.sucursal_id, ipi.presentacion_id, DATE(ipi.vencimiento) AS vencimiento_dia, "
                + "         MAX(ipi.cantidad) AS cantidad_inventario, MAX(ipi.vencimiento) AS vencimiento_inventario "
                + "  FROM operaciones.inventario_producto_item ipi "
                + "  JOIN operaciones.inventario_producto ip ON ip.id = ipi.inventario_producto_id "
                + "  JOIN operaciones.inventario inv ON inv.id = ip.inventario_id "
                + "  JOIN ultimo_inv ui ON ui.inventario_id = inv.id "
                + "  WHERE ipi.vencimiento IS NOT NULL "
                + "  GROUP BY inv.sucursal_id, ipi.presentacion_id, DATE(ipi.vencimiento) "
                + "), "
                + "candidatos AS ( "
                + construirSqlInventario(filtros)
                + "  UNION ALL "
                + construirSqlCompraNota(filtros)
                + "  UNION ALL "
                + construirSqlCompraRecepcion(filtros)
                + "  UNION ALL "
                + construirSqlTransferenciaSalida(filtros)
                + "  UNION ALL "
                + construirSqlTransferenciaIngreso(filtros)
                + "), "
                + "rankeados AS ( "
                + "  SELECT c.*, ui.fecha_inicio AS fecha_ultimo_inventario, "
                + "         ROW_NUMBER() OVER ( "
                + "           PARTITION BY c.sucursal_id, c.presentacion_id, DATE(c.vencimiento) "
                + "           ORDER BY "
                + "             CASE WHEN c.fuente_verdad <> 'INVENTARIO' AND c.fecha_fuente > ui.fecha_inicio THEN 0 ELSE 1 END, "
                + "             c.fecha_fuente DESC "
                + "         ) AS rn "
                + "  FROM candidatos c "
                + "  JOIN ultimo_inv ui ON ui.sucursal_id = c.sucursal_id "
                + ") "
                + "SELECT registro_id, fuente_verdad, presentacion_id, producto_id, producto_descripcion, codigo_barras, "
                + "       presentacion_cantidad, cantidad, vencimiento, inventario_producto_id, sucursal_id, sucursal_nombre, "
                + "       sector_descripcion, zona_descripcion, usuario_id, usuario_nickname, inventario_id, fecha_fuente, origen_id, "
                + "       cantidad_inventario, vencimiento_inventario, tipo_movimiento "
                + "FROM rankeados WHERE rn = 1 "
                + filtros.filtroFuenteVerdad();
    }

    private String construirSqlInventario(FiltrosProductosVencidos filtros) {
        return "  SELECT ipi.id AS registro_id, 'INVENTARIO' AS fuente_verdad, ipi.presentacion_id, pro.id AS producto_id, "
                + "         pro.descripcion AS producto_descripcion, cod.codigo AS codigo_barras, "
                + "         pre.cantidad AS presentacion_cantidad, ipi.cantidad, ipi.vencimiento, ip.id AS inventario_producto_id, "
                + "         s.id AS sucursal_id, s.nombre AS sucursal_nombre, "
                + "         COALESCE(sec_item.descripcion, sec_inv.descripcion) AS sector_descripcion, "
                + "         COALESCE(z_item.descripcion, z_inv.descripcion) AS zona_descripcion, "
                + "         u.id AS usuario_id, u.nickname AS usuario_nickname, inv.id AS inventario_id, "
                + "         inv.fecha_inicio AS fecha_fuente, CAST(NULL AS bigint) AS origen_id, "
                + "         snap.cantidad_inventario, snap.vencimiento_inventario, CAST(NULL AS text) AS tipo_movimiento "
                + "  FROM operaciones.inventario_producto_item ipi "
                + "  JOIN operaciones.inventario_producto ip ON ip.id = ipi.inventario_producto_id "
                + "  JOIN operaciones.inventario inv ON inv.id = ip.inventario_id "
                + "  JOIN ultimo_inv ui ON ui.inventario_id = inv.id "
                + "  JOIN empresarial.sucursal s ON s.id = inv.sucursal_id "
                + "  JOIN productos.presentacion pre ON pre.id = ipi.presentacion_id "
                + "  JOIN productos.producto pro ON pro.id = pre.producto_id "
                + "  LEFT JOIN productos.codigo cod ON cod.presentacion_id = pre.id AND cod.principal = true AND cod.activo = true "
                + "  LEFT JOIN empresarial.zona z_item ON z_item.id = ipi.zona_id "
                + "  LEFT JOIN empresarial.sector sec_item ON sec_item.id = z_item.sector_id "
                + "  JOIN empresarial.zona z_inv ON z_inv.id = ip.zona_id "
                + "  JOIN empresarial.sector sec_inv ON sec_inv.id = z_inv.sector_id "
                + "  LEFT JOIN personas.usuario u ON u.id = ipi.usuario_id "
                + "  LEFT JOIN inv_snap snap ON snap.sucursal_id = s.id AND snap.presentacion_id = ipi.presentacion_id "
                + "       AND snap.vencimiento_dia = DATE(ipi.vencimiento) "
                + "  WHERE ipi.vencimiento IS NOT NULL "
                + filtros.filtroVencimientoInventario()
                + filtros.filtroSucursal("s.id")
                + filtros.filtroSectorInventario()
                + filtros.filtroZonaInventario()
                + filtros.filtroUsuario("u.id")
                + filtros.filtroProducto("pro.id");
    }

    private String construirSqlCompraNota(FiltrosProductosVencidos filtros) {
        if (filtros.tieneFiltroSectorOZona()) {
            return construirSqlVacio();
        }
        return "  SELECT nrid.id, 'COMPRA', nri.presentacion_en_nota_id, nri.producto_id, pro.descripcion, cod.codigo, "
                + "         pre.cantidad, nrid.cantidad, CAST(nri.vencimiento_en_nota AS timestamp), CAST(NULL AS bigint), "
                + "         s.id, s.nombre, NULL, NULL, u.id, u.nickname, ui.inventario_id, "
                + "         COALESCE(nri.creado_en, nr.fecha), nr.id, "
                + "         snap.cantidad_inventario, snap.vencimiento_inventario, NULL "
                + "  FROM operaciones.nota_recepcion_item_distribucion nrid "
                + "  JOIN operaciones.nota_recepcion_item nri ON nri.id = nrid.nota_recepcion_item_id "
                + "  JOIN operaciones.nota_recepcion nr ON nr.id = nri.nota_recepcion_id "
                + "  JOIN ultimo_inv ui ON ui.sucursal_id = nrid.sucursal_entrega_id "
                + "  JOIN empresarial.sucursal s ON s.id = nrid.sucursal_entrega_id "
                + "  JOIN productos.producto pro ON pro.id = nri.producto_id "
                + "  JOIN productos.presentacion pre ON pre.id = nri.presentacion_en_nota_id "
                + "  LEFT JOIN productos.codigo cod ON cod.presentacion_id = pre.id AND cod.principal = true AND cod.activo = true "
                + "  LEFT JOIN personas.usuario u ON u.id = COALESCE(nrid.usuario_id, nri.usuario_id) "
                + "  LEFT JOIN inv_snap snap ON snap.sucursal_id = s.id AND snap.presentacion_id = nri.presentacion_en_nota_id "
                + "       AND snap.vencimiento_dia = nri.vencimiento_en_nota "
                + "  WHERE nri.vencimiento_en_nota IS NOT NULL "
                + "    AND nri.presentacion_en_nota_id IS NOT NULL "
                + "    AND COALESCE(nri.creado_en, nr.fecha) > ui.fecha_inicio "
                + "    AND NOT EXISTS ( "
                + "      SELECT 1 FROM operaciones.recepcion_mercaderia_item rmi "
                + "      WHERE rmi.nota_recepcion_item_id = nri.id "
                + "        AND rmi.sucursal_entrega_id = nrid.sucursal_entrega_id "
                + "        AND rmi.vencimiento_recibido IS NOT NULL "
                + "        AND COALESCE(rmi.cantidad_rechazada, 0) < rmi.cantidad_recibida "
                + "    ) "
                + filtros.filtroVencimientoGenerico("CAST(nri.vencimiento_en_nota AS timestamp)")
                + filtros.filtroSucursal("s.id")
                + filtros.filtroUsuario("u.id")
                + filtros.filtroProducto("pro.id");
    }

    private String construirSqlCompraRecepcion(FiltrosProductosVencidos filtros) {
        if (filtros.tieneFiltroSectorOZona()) {
            return construirSqlVacio();
        }
        return "  SELECT rmi.id, 'COMPRA', rmi.presentacion_recibida_id, pro.id, pro.descripcion, cod.codigo, "
                + "         pre.cantidad, rmi.cantidad_recibida, CAST(rmi.vencimiento_recibido AS timestamp), CAST(NULL AS bigint), "
                + "         s.id, s.nombre, NULL, NULL, u.id, u.nickname, ui.inventario_id, rm.fecha, rm.id, "
                + "         snap.cantidad_inventario, snap.vencimiento_inventario, NULL "
                + "  FROM operaciones.recepcion_mercaderia_item rmi "
                + "  JOIN operaciones.recepcion_mercaderia rm ON rm.id = rmi.recepcion_mercaderia_id "
                + "  JOIN ultimo_inv ui ON ui.sucursal_id = rmi.sucursal_entrega_id "
                + "  JOIN empresarial.sucursal s ON s.id = rmi.sucursal_entrega_id "
                + "  JOIN productos.producto pro ON pro.id = rmi.producto_id "
                + "  LEFT JOIN productos.presentacion pre ON pre.id = rmi.presentacion_recibida_id "
                + "  LEFT JOIN productos.codigo cod ON cod.presentacion_id = pre.id AND cod.principal = true AND cod.activo = true "
                + "  LEFT JOIN personas.usuario u ON u.id = rmi.usuario_id "
                + "  LEFT JOIN inv_snap snap ON snap.sucursal_id = s.id AND snap.presentacion_id = rmi.presentacion_recibida_id "
                + "       AND snap.vencimiento_dia = rmi.vencimiento_recibido "
                + "  WHERE rmi.vencimiento_recibido IS NOT NULL "
                + "    AND rm.fecha > ui.fecha_inicio "
                + "    AND COALESCE(rmi.cantidad_rechazada, 0) < rmi.cantidad_recibida "
                + filtros.filtroVencimientoGenerico("CAST(rmi.vencimiento_recibido AS timestamp)")
                + filtros.filtroSucursal("s.id")
                + filtros.filtroUsuario("u.id")
                + filtros.filtroProducto("pro.id");
    }

    private String construirSqlTransferenciaSalida(FiltrosProductosVencidos filtros) {
        if (filtros.tieneFiltroSectorOZona()) {
            return construirSqlVacio();
        }
        return "  SELECT ti.id, 'TRANSFERENCIA', ti.presentacion_pre_transferencia_id, pro.id, pro.descripcion, cod.codigo, "
                + "         pre.cantidad, ti.cantidad_pre_transferencia, ti.vencimiento_pre_transferencia, CAST(NULL AS bigint), "
                + "         s.id, s.nombre, NULL, NULL, u.id, u.nickname, ui.inventario_id, t.creado_en, t.id, "
                + "         snap.cantidad_inventario, snap.vencimiento_inventario, 'SALIDA' "
                + "  FROM operaciones.transferencia_item ti "
                + "  JOIN operaciones.transferencia t ON t.id = ti.transferencia_id "
                + "  JOIN ultimo_inv ui ON ui.sucursal_id = t.sucursal_origen_id "
                + "  JOIN empresarial.sucursal s ON s.id = t.sucursal_origen_id "
                + "  JOIN productos.presentacion pre ON pre.id = ti.presentacion_pre_transferencia_id "
                + "  JOIN productos.producto pro ON pro.id = pre.producto_id "
                + "  LEFT JOIN productos.codigo cod ON cod.presentacion_id = pre.id AND cod.principal = true AND cod.activo = true "
                + "  LEFT JOIN personas.usuario u ON u.id = ti.usuario_id "
                + "  LEFT JOIN inv_snap snap ON snap.sucursal_id = s.id AND snap.presentacion_id = ti.presentacion_pre_transferencia_id "
                + "       AND snap.vencimiento_dia = DATE(ti.vencimiento_pre_transferencia) "
                + "  WHERE ti.posee_vencimiento = true AND ti.activo = true "
                + "    AND CAST(t.estado AS text) <> 'CANCELADA' "
                + "    AND t.creado_en > ui.fecha_inicio "
                + "    AND ti.vencimiento_pre_transferencia IS NOT NULL "
                + filtros.filtroVencimientoGenerico("ti.vencimiento_pre_transferencia")
                + filtros.filtroSucursal("s.id")
                + filtros.filtroUsuario("u.id")
                + filtros.filtroProducto("pro.id");
    }

    private String construirSqlTransferenciaIngreso(FiltrosProductosVencidos filtros) {
        if (filtros.tieneFiltroSectorOZona()) {
            return construirSqlVacio();
        }
        return "  SELECT ti.id, 'TRANSFERENCIA', COALESCE(ti.presentacion_recepcion_id, ti.presentacion_pre_transferencia_id), "
                + "         pro.id, pro.descripcion, cod.codigo, pre.cantidad, "
                + "         COALESCE(ti.cantidad_recepcion, ti.cantidad_transporte, ti.cantidad_preparacion, ti.cantidad_pre_transferencia), "
                + "         COALESCE(ti.vencimiento_recepcion, ti.vencimiento_transporte, ti.vencimiento_preparacion, ti.vencimiento_pre_transferencia), "
                + "         CAST(NULL AS bigint), s.id, s.nombre, NULL, NULL, u.id, u.nickname, ui.inventario_id, t.creado_en, t.id, "
                + "         snap.cantidad_inventario, snap.vencimiento_inventario, 'INGRESO' "
                + "  FROM operaciones.transferencia_item ti "
                + "  JOIN operaciones.transferencia t ON t.id = ti.transferencia_id "
                + "  JOIN ultimo_inv ui ON ui.sucursal_id = t.sucursal_destino_id "
                + "  JOIN empresarial.sucursal s ON s.id = t.sucursal_destino_id "
                + "  LEFT JOIN productos.presentacion pre ON pre.id = COALESCE(ti.presentacion_recepcion_id, ti.presentacion_pre_transferencia_id) "
                + "  LEFT JOIN productos.producto pro ON pro.id = pre.producto_id "
                + "  LEFT JOIN productos.codigo cod ON cod.presentacion_id = pre.id AND cod.principal = true AND cod.activo = true "
                + "  LEFT JOIN personas.usuario u ON u.id = ti.usuario_id "
                + "  LEFT JOIN inv_snap snap ON snap.sucursal_id = s.id "
                + "       AND snap.presentacion_id = COALESCE(ti.presentacion_recepcion_id, ti.presentacion_pre_transferencia_id) "
                + "       AND snap.vencimiento_dia = DATE(COALESCE(ti.vencimiento_recepcion, ti.vencimiento_transporte, ti.vencimiento_preparacion, ti.vencimiento_pre_transferencia)) "
                + "  WHERE ti.posee_vencimiento = true AND ti.activo = true "
                + "    AND CAST(t.estado AS text) <> 'CANCELADA' "
                + "    AND t.creado_en > ui.fecha_inicio "
                + "    AND COALESCE(ti.vencimiento_recepcion, ti.vencimiento_transporte, ti.vencimiento_preparacion, ti.vencimiento_pre_transferencia) IS NOT NULL "
                + "    AND t.sucursal_destino_id <> t.sucursal_origen_id "
                + filtros.filtroVencimientoGenerico(
                        "COALESCE(ti.vencimiento_recepcion, ti.vencimiento_transporte, ti.vencimiento_preparacion, ti.vencimiento_pre_transferencia)")
                + filtros.filtroSucursal("s.id")
                + filtros.filtroUsuario("u.id")
                + filtros.filtroProducto("pro.id");
    }

    private String construirSqlVacio() {
        return "  SELECT CAST(NULL AS bigint), 'INVENTARIO', CAST(NULL AS bigint), CAST(NULL AS bigint), CAST(NULL AS varchar), CAST(NULL AS varchar), "
                + "         CAST(NULL AS numeric), CAST(NULL AS numeric), CAST(NULL AS timestamp), CAST(NULL AS bigint), CAST(NULL AS bigint), CAST(NULL AS varchar), "
                + "         CAST(NULL AS varchar), CAST(NULL AS varchar), CAST(NULL AS bigint), CAST(NULL AS varchar), CAST(NULL AS bigint), CAST(NULL AS timestamp), CAST(NULL AS bigint), "
                + "         CAST(NULL AS numeric), CAST(NULL AS timestamp), CAST(NULL AS text) "
                + "  WHERE 1 = 0 ";
    }

    private void aplicarParametros(Query query, Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }

    private ProductoVencidoViewDTO mapearFila(Object[] row) {
        ProductoVencidoViewDTO dto = new ProductoVencidoViewDTO();
        dto.setId(toLong(row[0]));
        dto.setFuenteVerdad(FuenteVerdadVencimiento.valueOf(Objects.toString(row[1], "INVENTARIO")));
        dto.setPresentacionId(toLong(row[2]));
        dto.setProductoId(toLong(row[3]));
        dto.setProductoDescripcion(Objects.toString(row[4], null));
        dto.setCodigoBarras(Objects.toString(row[5], null));
        dto.setPresentacionCantidad(toDouble(row[6]));
        dto.setCantidad(toDouble(row[7]));
        dto.setVencimiento(toLocalDateTime(row[8]));
        dto.setInventarioProductoId(toLong(row[9]));
        dto.setSucursalId(toLong(row[10]));
        dto.setSucursalNombre(Objects.toString(row[11], null));
        dto.setSectorDescripcion(Objects.toString(row[12], null));
        dto.setZonaDescripcion(Objects.toString(row[13], null));
        dto.setUsuarioId(toLong(row[14]));
        dto.setUsuarioNickname(Objects.toString(row[15], null));
        dto.setInventarioId(toLong(row[16]));
        dto.setFechaFuente(toLocalDateTime(row[17]));
        dto.setOrigenId(toLong(row[18]));
        dto.setCantidadInventario(toDouble(row[19]));
        dto.setVencimientoInventario(toLocalDateTime(row[20]));
        dto.setDetalleFuente(construirDetalleFuente(dto, Objects.toString(row[21], null)));
        return dto;
    }

    private ProductoVencidoViewDTO enriquecer(ProductoVencidoViewDTO dto) {
        if (dto.getVencimiento() != null) {
            long dias = ChronoUnit.DAYS.between(LocalDate.now(), dto.getVencimiento().toLocalDate());
            dto.setDiasVencimiento((int) dias);
            dto.setDiasVencimientoTexto(construirDiasVencimientoTexto(dias));
            dto.setDiasVencimientoClase(construirDiasVencimientoClase(dias));
            dto.setVencimientoColor(construirVencimientoColor(dias));
        }
        if (dto.getFuenteVerdad() != FuenteVerdadVencimiento.INVENTARIO
                && dto.getCantidadInventario() != null
                && dto.getVencimientoInventario() != null) {
            dto.setReferenciaInventario(String.format(
                    "Según inventario: %.0f uds vencen %s",
                    dto.getCantidadInventario(),
                    dto.getVencimientoInventario().toLocalDate().format(FECHA_FORMATO)));
        }
        return dto;
    }

    private String construirDetalleFuente(ProductoVencidoViewDTO dto, @Nullable String tipoMovimiento) {
        String fecha = dto.getFechaFuente() != null ? dto.getFechaFuente().toLocalDate().format(FECHA_FORMATO) : "-";
        switch (dto.getFuenteVerdad()) {
            case COMPRA:
                return String.format("Nota de compra #%s (%s)", dto.getOrigenId(), fecha);
            case TRANSFERENCIA:
                if ("SALIDA".equals(tipoMovimiento)) {
                    return String.format("Transferencia #%s - retiro (%s)", dto.getOrigenId(), fecha);
                }
                return String.format("Transferencia #%s - ingreso (%s)", dto.getOrigenId(), fecha);
            case INVENTARIO:
            default:
                return String.format("Inventario #%s (%s)", dto.getInventarioId(), fecha);
        }
    }

    private String construirDiasVencimientoTexto(long dias) {
        if (dias < 0) {
            long abs = Math.abs(dias);
            return "Vencido hace " + abs + (abs == 1 ? " día" : " días");
        }
        if (dias == 0) {
            return "Vence hoy";
        }
        return dias + (dias == 1 ? " día restante" : " días restantes");
    }

    private String construirDiasVencimientoClase(long dias) {
        if (dias < 0) {
            return "dias-vencimiento-cell vencido";
        }
        if (dias <= 7) {
            return "dias-vencimiento-cell por-vencer";
        }
        return "dias-vencimiento-cell vigente";
    }

    private String construirVencimientoColor(long dias) {
        if (dias < 0) {
            return COLOR_DANGER;
        }
        if (dias <= 7) {
            return COLOR_WARNING;
        }
        return COLOR_SUCCESS;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).doubleValue();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) value).getTime()).toLocalDateTime();
        }
        return null;
    }

    private static final class FiltrosProductosVencidos {
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;
        private final List<Long> sucursalIdList;
        private final List<Long> sectorIdList;
        private final List<Long> zonaIdList;
        private final List<Long> usuarioIdList;
        private final List<Long> productoIdList;
        private final List<String> fuenteVerdadList;
        private final Boolean soloRealmenteVencidos;

        private FiltrosProductosVencidos(
                LocalDateTime startDate,
                LocalDateTime endDate,
                List<Long> sucursalIdList,
                List<Long> sectorIdList,
                List<Long> zonaIdList,
                List<Long> usuarioIdList,
                List<Long> productoIdList,
                List<String> fuenteVerdadList,
                Boolean soloRealmenteVencidos) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.sucursalIdList = sucursalIdList;
            this.sectorIdList = sectorIdList;
            this.zonaIdList = zonaIdList;
            this.usuarioIdList = usuarioIdList;
            this.productoIdList = productoIdList;
            this.fuenteVerdadList = fuenteVerdadList;
            this.soloRealmenteVencidos = soloRealmenteVencidos;
        }

        Map<String, Object> parametros() {
            Map<String, Object> params = new HashMap<>();
            if (startDate != null && endDate != null) {
                params.put("startDate", startDate);
                params.put("endDate", endDate);
            }
            if (sucursalIdList != null && !sucursalIdList.isEmpty()) {
                params.put("sucursalIds", sucursalIdList);
            }
            if (sectorIdList != null && !sectorIdList.isEmpty()) {
                params.put("sectorIds", sectorIdList);
            }
            if (zonaIdList != null && !zonaIdList.isEmpty()) {
                params.put("zonaIds", zonaIdList);
            }
            if (usuarioIdList != null && !usuarioIdList.isEmpty()) {
                params.put("usuarioIds", usuarioIdList);
            }
            if (productoIdList != null && !productoIdList.isEmpty()) {
                params.put("productoIds", productoIdList);
            }
            if (fuenteVerdadList != null && !fuenteVerdadList.isEmpty()) {
                params.put("fuenteVerdadList", fuenteVerdadList);
            }
            return params;
        }

        String filtroFuenteVerdad() {
            if (fuenteVerdadList == null || fuenteVerdadList.isEmpty()) {
                return "";
            }
            return " AND fuente_verdad IN (:fuenteVerdadList) ";
        }

        boolean tieneFiltroSectorOZona() {
            return (sectorIdList != null && !sectorIdList.isEmpty())
                    || (zonaIdList != null && !zonaIdList.isEmpty());
        }

        String filtroVencimientoInventario() {
            if (startDate != null && endDate != null) {
                return " AND ipi.vencimiento BETWEEN :startDate AND :endDate ";
            }
            if (Boolean.TRUE.equals(soloRealmenteVencidos)) {
                return " AND (ipi.vencimiento < CURRENT_TIMESTAMP OR CAST(ipi.estado AS text) = 'VENCIDO') ";
            }
            return "";
        }

        String filtroVencimientoGenerico(String columna) {
            if (startDate != null && endDate != null) {
                return " AND " + columna + " BETWEEN :startDate AND :endDate ";
            }
            if (Boolean.TRUE.equals(soloRealmenteVencidos)) {
                return " AND " + columna + " < CURRENT_TIMESTAMP ";
            }
            return "";
        }

        String filtroSucursal(String columna) {
            if (sucursalIdList == null || sucursalIdList.isEmpty()) {
                return "";
            }
            return " AND " + columna + " IN (:sucursalIds) ";
        }

        String filtroSectorInventario() {
            if (sectorIdList == null || sectorIdList.isEmpty()) {
                return "";
            }
            return " AND COALESCE(sec_item.id, sec_inv.id) IN (:sectorIds) ";
        }

        String filtroZonaInventario() {
            if (zonaIdList == null || zonaIdList.isEmpty()) {
                return "";
            }
            return " AND COALESCE(z_item.id, z_inv.id) IN (:zonaIds) ";
        }

        String filtroUsuario(String columna) {
            if (usuarioIdList == null || usuarioIdList.isEmpty()) {
                return "";
            }
            return " AND " + columna + " IN (:usuarioIds) ";
        }

        String filtroProducto(String columna) {
            if (productoIdList == null || productoIdList.isEmpty()) {
                return "";
            }
            return " AND " + columna + " IN (:productoIds) ";
        }
    }
}
