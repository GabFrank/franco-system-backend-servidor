package com.franco.dev.service.operaciones.compras;

import com.franco.dev.graphql.operaciones.compras.RenglonFacturaMobile;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Avisos «para revisar» que salieron de probar las facturas de MARVI, GENFI y CAMPOS (2026-10-23). */
class AlertasRenglonTest {

    @Test
    void lasDescripcionesAbreviadasDelProveedorCompartenNombreConElProducto() {
        String[][] pares = {
                {"TL BUBBLEFUN 16X14.7 (12) PAR", "TOPLINE SEVEN 14.7G BUBBLE FUN"},
                {"MOÑO BOB 5X15G  (24)", "ARCOR BON O BON MOÑO 15G"},
                {"CAR.MAST.FTA ARC.800G  FILI(10)", "ARCOR CARAMELOS MASTICABLES FRUTALES"},
                {"ALF. MINITORTA CLA 69GR  (21)", "ALFAJOR AGUILA CLASICA"},
                {"PIR PLUT EXPL FRAMBO 15X11G (12)", "ARCOR PIRULIN PLUTONIO EXPLOSIVO FRAMBUESA 11G"},
                {"CAR M.PLUS SANDIA 12X29,4G  (12)", "ARCOR MENTHOPLUS SANDIA"},
                {"BONOBON ORIGINAL 30X15G (12)", "BON O BON ORIGINAL AMARILLO 15 G"},
                {"MISTER POP´S CHICLE 12*50UN", "MR POPS CHUPETIN C/  CHICLE"},
                {"BRAHMITA ULTRA CERO CAN X12 269CC TMC", "BRAHMITA ULTRA CERO LATA 269 ML"},
                {"SALADIX CALABREZA 6*100GR", "SALADIX CALABRESA 100G"},
        };
        for (String[] p : pares) {
            assertTrue(Similitud.compartenNombre(p[0], p[1]), p[0] + " ↔ " + p[1]);
        }
    }

    @Test
    void unCodigoDeBarrasDeOtroProductoNoCompartenNombre() {
        // GENFI manda en las gomas el código de barras de las Hogareñas 7 semillas
        assertFalse(Similitud.compartenNombre("GOMAS MOGUL COLA 12X30G (12)", "ARCOR HOGAREÑAS 7 SEMILLAS 189G"));
    }

    @Test
    void sinDescripcionNoSeAlerta() {
        assertTrue(Similitud.compartenNombre(null, "ARCOR"));
        assertTrue(Similitud.compartenNombre("12X30G", "ARCOR"));
    }

    private static RenglonFacturaMobile renglon(int orden, String codigo, String descripcion, Long productoId,
                                                boolean bonificacion) {
        RenglonFacturaMobile r = new RenglonFacturaMobile();
        r.setOrden(orden);
        r.setCodigoProveedor(codigo);
        r.setDescripcion(descripcion);
        r.setProductoId(productoId);
        r.setProductoDescripcion(productoId != null ? "POLIFOAM ESPONJA DE ACERO 9 G." : null);
        r.setEsBonificacion(bonificacion);
        return r;
    }

    @Test
    void dosCodigosDelProveedorAlMismoProductoSeAlertan() {
        List<RenglonFacturaMobile> rs = Arrays.asList(
                renglon(18, "7840763001110", "POLIFOAM ESPONJA DE ACERO INOX C/60", 6744L, false),
                renglon(19, "7840763007655", "POLIFOAM ESPONJA DE ACERO ECONOMICO C/96", 6744L, false));
        ComprasMobileService.alertarProductoRepetido(rs);
        assertEquals(1, rs.get(0).getAlertas().size());
        assertTrue(rs.get(0).getAlertas().get(0).contains("#19"));
        assertTrue(rs.get(1).getAlertas().get(0).contains("#18"));
    }

    @Test
    void elMismoCodigoConSuBonificacionNoSeAlerta() {
        List<RenglonFacturaMobile> rs = Arrays.asList(
                renglon(2, "7841503002169", "SENSEI PAPAS FRITAS ORIGINAL", 5183L, false),
                renglon(3, "7841503002169", "SENSEI PAPAS FRITAS ORIGINAL", 5183L, true),
                renglon(4, "7841503002176", "SENSEI PAPAS FRITAS CEBOLLA", 5184L, false),
                renglon(5, null, "SIN PRODUCTO", null, false));
        ComprasMobileService.alertarProductoRepetido(rs);
        rs.forEach(r -> assertTrue(r.getAlertas().isEmpty()));
    }
}
