package com.franco.dev.service.financiero;

import com.franco.dev.domain.administrativo.enums.Turno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Turnos de apertura de caja para el filtro de la lista de Cajas.
 *
 * {@code app.caja.turnos} (env CAJA_TURNOS) lista la hora de corte de cada turno: el turno va desde su
 * corte hasta el corte siguiente, dando la vuelta a la medianoche. Los cortes de bodega salen de las
 * aperturas reales de 2025-11 a 2026-11 y caen en franjas en que casi nadie abre: DIA 07:00 (1 h antes
 * de las 08), NOCHE 15:30, MADRUGADA 23:00. Farmacia tiene otros horarios y los fija en su .env.
 *
 * Un valor mal escrito no impide arrancar: se loguea y el filtro queda sin turnos.
 */
@Component
public class CajaTurnos {

    private static final Logger log = LoggerFactory.getLogger(CajaTurnos.class);
    private static final int DIA_MIN = 1440;

    /** Turno con su corte, en minutos del día; {@code hasta} es el corte siguiente (excluido). */
    public static class Ventana {
        private final Turno turno;
        private final int desde;
        private final int hasta;

        Ventana(Turno turno, int desde, int hasta) {
            this.turno = turno;
            this.desde = desde;
            this.hasta = hasta;
        }

        public Turno getTurno() { return turno; }
        public String getDesde() { return hhmm(desde); }
        public String getHasta() { return hhmm(hasta); }
    }

    private final List<Ventana> ventanas;
    private final String zona;

    public CajaTurnos(@Value("${app.caja.turnos:MADRUGADA=23:00,DIA=07:00,NOCHE=15:30}") String config,
                      @Value("${app.timezone:America/Asuncion}") String zona) {
        this.zona = zona;
        List<Ventana> v;
        try {
            v = parsear(config);
        } catch (RuntimeException e) {
            log.error("app.caja.turnos inválido '{}': el filtro de cajas por turno queda apagado ({})", config, e.getMessage());
            v = Collections.emptyList();
        }
        this.ventanas = v;
    }

    /** Zona en que se leen las horas de apertura: la del negocio, no la de la sesión de Postgres. */
    public String zona() {
        return zona;
    }

    /** Ordenadas por corte. Vacía si la configuración es inválida. */
    public List<Ventana> ventanas() {
        return ventanas;
    }

    /**
     * {offset, desde, hasta} para el JPQL: con r = (minuto del día + offset) mod 1440 el primer corte
     * pasa a ser 0 y cada turno es el rango desde ≤ r &lt; hasta. Sin turno: todo el día. Turno que esta
     * instancia no tiene: rango vacío.
     */
    public int[] rango(Turno turno) {
        if (turno == null) return new int[]{0, 0, DIA_MIN};
        if (ventanas.isEmpty()) return new int[]{0, 0, 0};
        int primero = ventanas.get(0).desde;
        int offset = (DIA_MIN - primero) % DIA_MIN;
        for (int i = 0; i < ventanas.size(); i++) {
            Ventana w = ventanas.get(i);
            if (w.turno != turno) continue;
            int desde = w.desde - primero;
            int hasta = i + 1 < ventanas.size() ? ventanas.get(i + 1).desde - primero : DIA_MIN;
            return new int[]{offset, desde, hasta};
        }
        return new int[]{offset, 0, 0};
    }

    static List<Ventana> parsear(String config) {
        List<int[]> cortes = new ArrayList<>();
        List<Turno> turnos = new ArrayList<>();
        for (String parte : config.split(",")) {
            String[] kv = parte.trim().split("=");
            if (kv.length != 2) throw new IllegalArgumentException("se esperaba TURNO=HH:mm en '" + parte + "'");
            Turno t = Turno.valueOf(kv[0].trim());
            if (turnos.contains(t)) throw new IllegalArgumentException("turno repetido " + t);
            String[] hm = kv[1].trim().split(":");
            if (hm.length != 2) throw new IllegalArgumentException("hora inválida '" + kv[1] + "'");
            int h = Integer.parseInt(hm[0]);
            int m = Integer.parseInt(hm[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) throw new IllegalArgumentException("hora inválida '" + kv[1] + "'");
            turnos.add(t);
            cortes.add(new int[]{turnos.size() - 1, h * 60 + m});
        }
        cortes.sort(Comparator.comparingInt(c -> c[1]));
        List<Ventana> out = new ArrayList<>();
        for (int i = 0; i < cortes.size(); i++) {
            int desde = cortes.get(i)[1];
            int hasta = cortes.get((i + 1) % cortes.size())[1];
            if (desde == hasta && cortes.size() > 1) throw new IllegalArgumentException("dos turnos con el mismo corte");
            out.add(new Ventana(turnos.get(cortes.get(i)[0]), desde, hasta));
        }
        return Collections.unmodifiableList(out);
    }

    private static String hhmm(int minutos) {
        return String.format("%02d:%02d", minutos / 60, minutos % 60);
    }
}
