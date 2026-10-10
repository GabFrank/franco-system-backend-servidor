package com.franco.dev.service.operaciones;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Deja lista al arrancar la secuencia que numera las solicitudes de pago: la crea si falta y la adelanta si
 * quedó detrás del número más alto. Solo adelanta, nunca retrocede.
 *
 * <p>La migración V242.1 ya la crea y la alinea, pero Flyway no repite una versión aplicada. Esto cubre lo
 * que queda afuera: un entorno donde la migración no corrió, y la vuelta a este JAR después de un rollback en
 * el que el anterior siguió numerando por conteo. Sin la secuencia no entra ninguna solicitud; atrasada, las
 * altas fallan hasta pasar el número más alto.</p>
 *
 * <p>No frena el arranque: si no puede, lo dice con ERROR y el alta fallará con el mensaje de la base. Crear
 * y alinear son dos pasos separados: que falle el primero no impide el segundo.</p>
 */
@Component
@Lazy(false)   // la aplicación arranca con lazy-initialization: sin esto nadie lo instancia y no corre
@RequiredArgsConstructor
public class SolicitudPagoNumeroVerificador {

    private static final Logger log = LoggerFactory.getLogger(SolicitudPagoNumeroVerificador.class);

    static final String SECUENCIA = "operaciones.solicitud_pago_numero_seq";
    static final String EXISTE = "select to_regclass('" + SECUENCIA + "') is not null";
    static final String CREAR = "CREATE SEQUENCE IF NOT EXISTS " + SECUENCIA;
    /**
     * Una sola sentencia: adelanta la secuencia al número más alto con forma SP-<dígitos> más uno (el mismo
     * criterio que la migración) <b>solo si</b> hoy daría uno menor, y devuelve el valor nuevo; sin filas si
     * no hizo falta. Leer primero y adelantar después, en dos pasos, podía hacerla retroceder si entraba un
     * alta en el medio.
     */
    static final String ADELANTAR_SI_ATRASADA = "select setval('" + SECUENCIA + "', m.minimo, false) "
            + "from (select coalesce(max(case when numero_solicitud ~ '^SP-[0-9]{1,15}$' "
            + "then substring(numero_solicitud from 4)::bigint end), 0) + 1 as minimo "
            + "from operaciones.solicitud_pago) m "
            + "where m.minimo > (select case when is_called then last_value + 1 else last_value end from " + SECUENCIA + ")";

    private final JdbcTemplate jdbc;

    /**
     * En {@code @PostConstruct} y no al quedar lista la aplicación: corre antes de que el servidor web acepte
     * pedidos, así que ninguna alta se cruza con el ajuste. El {@code JdbcTemplate} ya espera a Flyway.
     */
    @PostConstruct
    public void alinear() {
        try {
            if (!Boolean.TRUE.equals(jdbc.queryForObject(EXISTE, Boolean.class))) {
                jdbc.execute(CREAR);
                log.warn("Faltaba la secuencia {} (migracion V242.1): se creo.", SECUENCIA);
            }
        } catch (Exception e) {
            log.error("No se pudo crear la secuencia {} (migracion V242.1): sin ella no se pueden crear "
                    + "solicitudes de pago. Motivo: {}", SECUENCIA, e.getMessage());
        }
        try {
            List<Long> adelantada = jdbc.queryForList(ADELANTAR_SI_ATRASADA, Long.class);
            if (!adelantada.isEmpty()) {
                log.warn("La secuencia {} estaba detras del numero de solicitud mas alto: se adelanto a {}.",
                        SECUENCIA, adelantada.get(0));
            }
        } catch (Exception e) {
            log.error("No se pudo alinear la secuencia {}: si quedo detras del numero mas alto, las altas de "
                    + "solicitudes de pago van a fallar hasta pasarlo. Motivo: {}", SECUENCIA, e.getMessage());
        }
    }
}
