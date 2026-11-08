package com.franco.dev.service.financiero;

import com.franco.dev.domain.administrativo.enums.Turno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CajaTurnosTest {

    private static final String BODEGA = "MADRUGADA=23:00,DIA=07:00,NOCHE=15:30";
    private static final String FARMACIA = "DIA=04:00,NOCHE=12:30";

    /** Lo mismo que evalúa el JPQL de PdvCajaRepository.findAllWithFilters. */
    private static boolean entra(CajaTurnos t, Turno turno, String hhmm) {
        int[] r = t.rango(turno);
        String[] hm = hhmm.split(":");
        int m = Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
        int rot = (m + r[0]) % 1440;
        return rot >= r[1] && rot < r[2];
    }

    private static List<Turno> turnosDe(CajaTurnos t, String hhmm) {
        List<Turno> out = new ArrayList<>();
        for (Turno turno : Turno.values()) if (entra(t, turno, hhmm)) out.add(turno);
        return out;
    }

    private static void es(CajaTurnos t, String hhmm, Turno esperado) {
        assertEquals(List.of(esperado), turnosDe(t, hhmm), hhmm);
    }

    @Test
    @DisplayName("bodega: los bordes caen en el turno que empieza")
    void bordesBodega() {
        CajaTurnos t = new CajaTurnos(BODEGA, "America/Asuncion");
        es(t, "06:59", Turno.MADRUGADA);
        es(t, "07:00", Turno.DIA);
        es(t, "08:00", Turno.DIA);
        es(t, "15:29", Turno.DIA);
        es(t, "15:30", Turno.NOCHE);
        es(t, "16:50", Turno.NOCHE);
        es(t, "22:59", Turno.NOCHE);
        es(t, "23:00", Turno.MADRUGADA);
        es(t, "00:00", Turno.MADRUGADA);
        es(t, "01:00", Turno.MADRUGADA);
    }

    @Test
    @DisplayName("cada minuto del día cae en un solo turno; sin turno entra todo")
    void particion() {
        for (String cfg : new String[]{BODEGA, FARMACIA}) {
            CajaTurnos t = new CajaTurnos(cfg, "America/Asuncion");
            for (int m = 0; m < 1440; m++) {
                String hhmm = String.format("%02d:%02d", m / 60, m % 60);
                assertEquals(1, turnosDe(t, hhmm).size(), cfg + " " + hhmm);
                int[] todo = t.rango(null);
                int rot = (m + todo[0]) % 1440;
                assertTrue(rot >= todo[1] && rot < todo[2]);
            }
        }
    }

    @Test
    @DisplayName("farmacia: sin madrugada, la tarde cruza la medianoche")
    void farmacia() {
        CajaTurnos t = new CajaTurnos(FARMACIA, "America/Asuncion");
        es(t, "03:59", Turno.NOCHE);
        es(t, "04:00", Turno.DIA);
        es(t, "06:50", Turno.DIA);
        es(t, "12:29", Turno.DIA);
        es(t, "12:30", Turno.NOCHE);
        es(t, "14:50", Turno.NOCHE);
        int[] r = t.rango(Turno.MADRUGADA);
        assertEquals(r[1], r[2], "un turno que la instancia no tiene da rango vacío");
        assertEquals(2, t.ventanas().size());
        assertEquals("12:30", t.ventanas().get(1).getDesde());
        assertEquals("04:00", t.ventanas().get(1).getHasta());
    }

    @Test
    @DisplayName("un valor inválido no tira: queda sin turnos y ningún turno trae cajas")
    void invalido() {
        for (String cfg : new String[]{"DIA=7", "TARDE=12:00", "DIA=25:00", "DIA=07:00,DIA=15:00", "DIA=07:00,NOCHE=07:00", ""}) {
            CajaTurnos t = new CajaTurnos(cfg, "America/Asuncion");
            assertTrue(t.ventanas().isEmpty(), cfg);
            assertTrue(turnosDe(t, "08:00").isEmpty(), cfg);
            int[] todo = t.rango(null);
            assertEquals(1440, todo[2] - todo[1], "sin turno sigue trayendo todo: " + cfg);
        }
    }
}
