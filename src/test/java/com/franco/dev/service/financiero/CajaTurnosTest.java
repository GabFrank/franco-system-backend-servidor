package com.franco.dev.service.financiero;

import com.franco.dev.domain.administrativo.enums.Turno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CajaTurnosTest {

    /** Lo mismo que evalúa el JPQL de PdvCajaRepository.findAllWithFilters. */
    private static boolean entra(Turno turno, int minuto) {
        int[] r = CajaTurnos.rango(turno);
        int rot = (minuto + r[0]) % 1440;
        return rot >= r[1] && rot < r[2];
    }

    private static List<Turno> turnosDe(String hhmm) {
        String[] hm = hhmm.split(":");
        int m = Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
        List<Turno> out = new ArrayList<>();
        for (Turno t : Turno.values()) if (entra(t, m)) out.add(t);
        return out;
    }

    private static void es(String hhmm, Turno esperado) {
        assertEquals(List.of(esperado), turnosDe(hhmm), hhmm);
    }

    @Test
    @DisplayName("los bordes caen en el turno que empieza")
    void bordes() {
        es("03:59", Turno.MADRUGADA);
        es("04:00", Turno.DIA);
        es("06:50", Turno.DIA);   // mañana de farmacia
        es("07:50", Turno.DIA);   // día de bodega
        es("12:29", Turno.DIA);
        es("12:30", Turno.NOCHE);
        es("14:50", Turno.NOCHE); // tarde de farmacia
        es("16:50", Turno.NOCHE); // noche de bodega
        es("22:59", Turno.NOCHE);
        es("23:00", Turno.MADRUGADA);
        es("00:00", Turno.MADRUGADA);
    }

    @Test
    @DisplayName("cada minuto del día cae en un solo turno; sin turno entra todo")
    void particion() {
        for (int m = 0; m < 1440; m++) {
            int n = 0;
            for (Turno t : Turno.values()) if (entra(t, m)) n++;
            assertEquals(1, n, "minuto " + m);
            assertTrue(entra(null, m));
        }
    }
}
