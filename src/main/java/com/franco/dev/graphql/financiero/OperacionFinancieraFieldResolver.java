package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.OperacionFinanciera;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import graphql.kickstart.tools.GraphQLResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Campos derivados de {@link OperacionFinanciera}.
 *
 * <p>{@code anulacion}: la operación solo guarda {@code anulado}. Quién, cuándo y por qué quedan en los
 * contra-movimientos que crean {@code TesoreriaService.revertir} y {@code BancoLedgerService.revertir} al anularla
 * (todas las patas en la misma transacción, con el mismo usuario y motivo), así que se lee el primero: de caja si la
 * operación tiene patas de caja, si no de banco. Sirve también para las anuladas antes de este campo.</p>
 */
@Component
@AllArgsConstructor
public class OperacionFinancieraFieldResolver implements GraphQLResolver<OperacionFinanciera> {

    /** El texto que arman los dos revertir: {@code "ANULACION: " + motivo + " (mov #" + id + ")"}. */
    private static final Pattern DESCRIPCION_ANULACION = Pattern.compile("^ANULACION: (.*) \\(mov #\\d+\\)$", Pattern.DOTALL);

    private final MovimientoCajaVirtualRepository movimientoCajaRepository;
    private final MovimientoBancarioRepository movimientoBancarioRepository;

    public OperacionFinancieraAnulacion anulacion(OperacionFinanciera op) {
        if (op == null || op.getId() == null || !Boolean.TRUE.equals(op.getAnulado())) return null;
        return movimientoCajaRepository.findAnulacionesDeOperacion(op.getId()).stream().findFirst()
                .map(c -> new OperacionFinancieraAnulacion(motivo(c.getDescripcion()), c.getUsuario(), c.getCreadoEn()))
                .orElseGet(() -> movimientoBancarioRepository.findAnulacionesDeOperacion(op.getId()).stream().findFirst()
                        .map(b -> new OperacionFinancieraAnulacion(motivo(b.getDescripcion()), b.getUsuario(), b.getCreadoEn()))
                        .orElse(null));
    }

    /** El motivo sin el envoltorio de revertir; otro formato (p. ej. truncado a 300 en banco) se devuelve entero. */
    static String motivo(String descripcion) {
        if (descripcion == null) return null;
        Matcher m = DESCRIPCION_ANULACION.matcher(descripcion);
        String motivo = (m.matches() ? m.group(1) : descripcion).trim();
        return motivo.isEmpty() ? null : motivo;
    }
}
