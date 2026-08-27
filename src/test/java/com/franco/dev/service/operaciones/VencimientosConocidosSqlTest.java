package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La forma del SQL de vencimientosConocidos.
 *
 * No se usa productosVencidos para proponer el vencimiento al contar porque
 * ese reporte ancla las cinco fuentes al ultimo inventario de la sucursal, y
 * la toma que se esta contando ES el ultimo inventario: mientras se cuenta
 * devuelve cero. Lo que estas pruebas fijan es que el modo historico no lleve
 * ese ancla, y que el reporte si la siga llevando.
 */
class VencimientosConocidosSqlTest {

    private String capturarSql(boolean historico) {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());
        when(query.getSingleResult()).thenReturn(0L);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.setFirstResult(org.mockito.ArgumentMatchers.anyInt())).thenReturn(query);
        when(query.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(query);

        ProductosVencidosService service = new ProductosVencidosService();
        ReflectionTestUtils.setField(service, "entityManager", em);

        if (historico) {
            service.vencimientosConocidos(1L, Arrays.asList(802L), 3);
        } else {
            service.buscarProductosVencidos(null, null, Collections.singletonList(1L), null, null, null,
                    Arrays.asList(802L), null, Boolean.FALSE,
                    org.springframework.data.domain.PageRequest.of(0, 50));
        }

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(em, org.mockito.Mockito.atLeastOnce()).createNativeQuery(sql.capture());
        return sql.getAllValues().get(sql.getAllValues().size() - 1);
    }

    @Test
    @DisplayName("el modo historico no ancla al ultimo inventario, en ninguna de las cinco fuentes")
    void historicoSinAncla() throws IOException {
        String sql = capturarSql(true);

        /*
         * El texto "> ui.fecha_inicio" sigue en las cuatro fuentes de compra y
         * transferencia, y esta bien: lo que se neutraliza es el ANCLA, que en
         * modo historico vale 1900, asi que el filtro pasa siempre. Afirmar el
         * ancla y no la ausencia del filtro es lo que hace que este test siga
         * valiendo si manana el SQL se reordena.
         */
        assertTrue(sql.contains("TIMESTAMP '1900-01-01' AS fecha_inicio"), sql);
        assertFalse(sql.contains("COALESCE(MAX(inv.fecha_inicio)"), sql);

        // Era el filtro que dejaba fuera las 8 compras de COCA COLA 500ML en
        // bodega3: todas anteriores a la toma que se estaba contando.
        // La fuente INVENTARIO mira cualquier toma concluida, no solo la ultima.
        assertTrue(sql.contains("AND inv.estado = 'CONCLUIDO'"), sql);
        assertFalse(sql.contains("JOIN ultimo_inv ui ON ui.inventario_id = inv.id   JOIN empresarial.sucursal"), sql);

        // Se deja el SQL a mano para poder correrlo contra la base.
        Files.write(Path.of("target", "vencimientos-conocidos.sql"), sql.getBytes());
    }

    @Test
    @DisplayName("el reporte de vencidos conserva su ancla: sigue siendo lo que entro desde el ultimo inventario")
    void reporteConservaElAncla() throws IOException {
        String sql = capturarSql(false);

        assertTrue(sql.contains("> ui.fecha_inicio"), sql);
        assertTrue(sql.contains("JOIN ultimo_inv ui ON ui.inventario_id = inv.id"), sql);
        // El ancla real, no la neutralizada del modo historico.
        assertTrue(sql.contains("COALESCE(MAX(inv.fecha_inicio)"), sql);

        Files.write(Path.of("target", "productos-vencidos.sql"), sql.getBytes());
    }

    @Test
    @DisplayName("el ancla solo cuenta inventarios concluidos, en los dos modos")
    void anclaSoloConcluidos() {
        // Una toma ABIERTA o CANCELADA no es un inventario hecho. Tomarla como
        // ancla dejaba el reporte en blanco para esa sucursal: en bodega3
        // pasaba en 5 de 26.
        for (boolean historico : new boolean[] { true, false }) {
            String sql = capturarSql(historico);
            assertTrue(sql.contains("MAX(inv.id) FILTER (WHERE inv.estado = 'CONCLUIDO')"), sql);
            // Ninguna sucursal puede desaparecer del reporte por no tener
            // ningun inventario concluido.
            assertTrue(sql.contains("TIMESTAMP '1900-01-01'"), sql);
        }
    }

    // ── el recorte ──────────────────────────────────────────────────────

    private static com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO fila(
            Long presentacionId, String vencimiento) {
        com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO dto =
                new com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO();
        dto.setPresentacionId(presentacionId);
        dto.setVencimiento(java.time.LocalDate.parse(vencimiento).atStartOfDay());
        return dto;
    }

    private static java.util.List<String> fechas(
            java.util.List<com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO> filas) {
        java.util.List<String> salida = new java.util.ArrayList<>();
        for (com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO f : filas) {
            salida.add(f.getVencimiento().toLocalDate().toString());
        }
        return salida;
    }

    private static final java.time.LocalDate HOY = java.time.LocalDate.of(2026, 8, 26);

    @Test
    @DisplayName("todas las vigentes entran, por muchas que sean")
    void todasLasVigentes() {
        // Son las que sirven para contar: recortarlas dejaria renglones sin
        // lote que proponer.
        java.util.List<com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO> filas = Arrays.asList(
                fila(1212L, "2026-09-10"), fila(1212L, "2026-10-01"),
                fila(1212L, "2026-11-20"), fila(1212L, "2027-01-05"));

        assertTrue(ProductosVencidosService.recortarPorPresentacion(filas, HOY, 3).size() == 4);
    }

    @Test
    @DisplayName("de las vencidas solo las mas recientes, que son las que pueden seguir en gondola")
    void soloLasVencidasMasRecientes() {
        java.util.List<com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO> filas = Arrays.asList(
                fila(1212L, "2026-08-25"), fila(1212L, "2026-07-22"), fila(1212L, "2026-07-13"),
                fila(1212L, "2026-07-11"), fila(1212L, "2023-06-25"));

        // Las de 2023 no le sirven a nadie frente a la gondola.
        org.junit.jupiter.api.Assertions.assertEquals(
                Arrays.asList("2026-08-25", "2026-07-22", "2026-07-13"),
                fechas(ProductosVencidosService.recortarPorPresentacion(filas, HOY, 3)));
    }

    @Test
    @DisplayName("lo que vence hoy todavia no vencio")
    void loQueVenceHoyEsVigente() {
        // La mercaderia esta en gondola y se puede vender durante el dia.
        java.util.List<com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO> filas =
                Collections.singletonList(fila(1212L, "2026-08-26"));

        org.junit.jupiter.api.Assertions.assertEquals(
                Arrays.asList("2026-08-26"),
                fechas(ProductosVencidosService.recortarPorPresentacion(filas, HOY, 0)));
    }

    @Test
    @DisplayName("el recorte es por presentacion: una con muchos lotes no se come el cupo de la otra")
    void elRecorteEsPorPresentacion() {
        // Es el caso real de COCA COLA 500ML en bodega3: la caja x 6 tiene 81
        // fechas conocidas y la unidad 21. Cortando sobre el total, la unidad
        // se quedaba sin ninguna.
        java.util.List<com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO> filas = Arrays.asList(
                fila(1212L, "2026-08-25"), fila(1212L, "2026-07-22"), fila(1212L, "2026-07-13"),
                fila(1212L, "2026-07-11"), fila(1211L, "2026-04-15"));

        java.util.List<com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO> recortadas =
                ProductosVencidosService.recortarPorPresentacion(filas, HOY, 3);

        assertTrue(fechas(recortadas).contains("2026-04-15"), fechas(recortadas).toString());
        org.junit.jupiter.api.Assertions.assertEquals(4, recortadas.size());
    }

    @Test
    @DisplayName("sin fecha de vencimiento no es una sugerencia")
    void sinFechaNoEsSugerencia() {
        com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO sinFecha =
                new com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO();
        sinFecha.setPresentacionId(1212L);

        org.junit.jupiter.api.Assertions.assertTrue(
                fechas(ProductosVencidosService.recortarPorPresentacion(
                        Arrays.asList(sinFecha, fila(1212L, "2026-09-10")), HOY, 3))
                        .contains("2026-09-10"));
    }
}
