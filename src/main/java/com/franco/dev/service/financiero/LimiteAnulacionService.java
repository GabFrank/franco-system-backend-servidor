package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Tope de antigüedad para anular (CN4, {@code configuracion_general.dias_limite_anulacion}). Único dueño
 * de la regla: la consultan las dos reversas ({@link TesoreriaService#revertir} y
 * {@link BancoLedgerService#revertir}), por donde pasa toda anulación que postea un contra-movimiento, y
 * los módulos dueños al entrar, con la fecha de su documento (issue #370).
 *
 * <p>Sin fila de configuración, con el límite en {@code null} o en {@code 0}, no hay tope.</p>
 */
@Service
@AllArgsConstructor
@Slf4j
public class LimiteAnulacionService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ConfiguracionGeneralRepository configRepository;

    /**
     * Rechaza si {@code fecha} es más vieja que el límite configurado. Una fecha nula no se puede medir y pasa.
     *
     * @param queCosa sujeto del mensaje, con artículo y mayúscula inicial («El pago #12»).
     */
    public void requireDentroDelLimite(LocalDateTime fecha, String queCosa) {
        if (fecha == null) return;
        Integer diasLimite = diasLimite();
        if (diasLimite == null || diasLimite <= 0) return;
        if (fecha.isBefore(LocalDateTime.now().minusDays(diasLimite))) {
            throw new GraphQLException(queCosa + " del " + fecha.format(FECHA) + " supera el límite de "
                    + diasLimite + " días para anular.");
        }
    }

    /** Días límite configurados, o null si no hay config/límite. */
    private Integer diasLimite() {
        try {
            return configRepository.findAll().stream().findFirst()
                    .map(ConfiguracionGeneral::getDiasLimiteAnulacion)
                    .orElse(null);
        } catch (Exception e) {
            // Sin poder leer la configuración no se bloquea la tesorería, pero que no pase en silencio.
            log.warn("No se pudo leer el límite de anulación; se anula sin tope de antigüedad", e);
            return null;
        }
    }
}
