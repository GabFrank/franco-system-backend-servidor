package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EntradaVaria;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.OperacionFinanciera;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.PrestamoEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.financiero.EntradaVariaRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.OperacionFinancieraRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.repository.rrhh.PrestamoRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.function.Supplier;

/**
 * Altas que un pedido repetido registraba otra vez (issue #376): entrada varia, operación financiera,
 * ingreso y egreso de maletín a mano, gasto y vale del hub de egresos, y préstamo con desembolso.
 *
 * <p>Cada método abre la transacción, toma la clave con {@link IdempotenciaService} —antes de cualquier
 * lock del negocio— y corre el alta de siempre adentro: si el alta se rechaza, la clave se va con el
 * rollback. Sin clave (cliente viejo) corre como antes.</p>
 *
 * <p>La huella la arma quien llama, con <b>lo que mandó el cliente</b> (los ids del input, no las
 * entidades resueltas) y antes de que el servicio derive nada: el reintento manda lo mismo.</p>
 *
 * <p>Si el pedido es una repetición se devuelve lo que creó el original, en el estado que tenga hoy.
 * Si el original se anuló después, se rechaza: ni éxito ni otro registro.</p>
 */
@Service
@RequiredArgsConstructor
public class AltaIdempotenteService {

    static final String OPERACION_ENTRADA_VARIA = "ENTRADA_VARIA";
    static final String OPERACION_OPERACION_FINANCIERA = "OPERACION_FINANCIERA";
    static final String OPERACION_MALETIN_INGRESO = "MALETIN_INGRESO";
    static final String OPERACION_MALETIN_EGRESO = "MALETIN_EGRESO";
    static final String OPERACION_GASTO_PARA_PAGO = "GASTO_PARA_PAGO";
    static final String OPERACION_VALE_PARA_PAGO = "VALE_PARA_PAGO";
    static final String OPERACION_PRESTAMO = "PRESTAMO_CON_DESEMBOLSO";
    /**
     * El número de una solicitud de pago («SP-») sale de contar las que hay: dos altas simultáneas contaban lo
     * mismo y una chocaba contra el índice único. Estas dos altas son transacciones cortas que todavía no
     * tomaron ningún lock del negocio, así que se ponen en fila acá. No va dentro de la numeración misma: los
     * pagos en lote de RRHH crean solicitudes con filas ya tomadas y lo retendrían hasta el final del lote.
     */
    static final String LOCK_NUMERO_SOLICITUD = "SOLICITUD_PAGO_NUMERO";

    private final IdempotenciaService idempotencia;
    private final EntradaVariaRepository entradaVariaRepository;
    private final OperacionFinancieraRepository operacionFinancieraRepository;
    private final MovimientoCajaVirtualRepository movimientoCajaVirtualRepository;
    private final SolicitudPagoRepository solicitudPagoRepository;
    private final ValeRepository valeRepository;
    private final PrestamoRepository prestamoRepository;
    private final BloqueoTransaccionalService bloqueo;

    @Transactional
    public EntradaVaria entradaVaria(String clave, String huella, Usuario usuario, Supplier<EntradaVaria> alta) {
        return idempotencia.ejecutar(clave, OPERACION_ENTRADA_VARIA, huella, usuario, alta, EntradaVaria::getId, id -> {
            // El estado, de la base: la entidad puede estar en la sesión con el valor de antes.
            rechazarSi(entradaVariaRepository.findAnuladoById(id).orElse(false), "La entrada o salida");
            return entradaVariaRepository.findById(id).orElse(null);
        });
    }

    @Transactional
    public OperacionFinanciera operacionFinanciera(String clave, String huella, Usuario usuario,
                                                   Supplier<OperacionFinanciera> alta) {
        return idempotencia.ejecutar(clave, OPERACION_OPERACION_FINANCIERA, huella, usuario, alta,
                OperacionFinanciera::getId, id -> {
                    rechazarSi(operacionFinancieraRepository.findAnuladoById(id).orElse(false), "La operación");
                    return operacionFinancieraRepository.findById(id).orElse(null);
                });
    }

    @Transactional
    public MovimientoCajaVirtual maletin(boolean ingreso, String clave, String huella, Usuario usuario,
                                         Supplier<MovimientoCajaVirtual> alta) {
        return idempotencia.ejecutar(clave, ingreso ? OPERACION_MALETIN_INGRESO : OPERACION_MALETIN_EGRESO, huella,
                usuario, alta, MovimientoCajaVirtual::getId, id -> {
                    MovimientoCajaVirtual m = movimientoCajaVirtualRepository.findById(id).orElse(null);
                    // Un movimiento no tiene «anulado»: la reversa lo deja inactivo. Nulo cuenta como activo.
                    rechazarSi(m != null && Boolean.FALSE.equals(m.getActivo()), "El movimiento");
                    return m;
                });
    }

    @Transactional
    public SolicitudPago gastoParaPago(String clave, String huella, Usuario usuario, Supplier<SolicitudPago> alta) {
        return idempotencia.ejecutar(clave, OPERACION_GASTO_PARA_PAGO, huella, usuario, enFilaPorNumero(alta), SolicitudPago::getId, id -> {
            SolicitudPago s = solicitudPagoRepository.findById(id).orElse(null);
            // Hoy ningún flujo cancela una solicitud de gasto; queda por si alguno lo hace.
            rechazarSi(s != null && s.getEstado() == SolicitudPagoEstado.CANCELADO, "El gasto");
            return s;
        });
    }

    @Transactional
    public Vale valeParaPago(String clave, String huella, Usuario usuario, Supplier<Vale> alta) {
        return idempotencia.ejecutar(clave, OPERACION_VALE_PARA_PAGO, huella, usuario, enFilaPorNumero(alta), Vale::getId, id -> {
            Vale v = valeRepository.findById(id).orElse(null);
            rechazarSi(v != null && v.getEstado() == ValeEstado.ANULADO, "El vale");
            return v;
        });
    }

    @Transactional
    public Prestamo prestamo(String clave, String huella, Usuario usuario, Supplier<Prestamo> alta) {
        return idempotencia.ejecutar(clave, OPERACION_PRESTAMO, huella, usuario, alta, Prestamo::getId, id -> {
            Prestamo p = prestamoRepository.findById(id).orElse(null);
            // Hoy ningún flujo cancela un préstamo; queda por si alguno lo hace.
            rechazarSi(p != null && p.getEstado() == PrestamoEstado.CANCELADO, "El préstamo");
            return p;
        });
    }

    private <T> Supplier<T> enFilaPorNumero(Supplier<T> alta) {
        return () -> {
            bloqueo.tomar(LOCK_NUMERO_SOLICITUD);
            return alta.get();
        };
    }

    private static void rechazarSi(boolean anulado, String que) {
        if (anulado) {
            throw new GraphQLException(que + " de este pedido ya se había registrado y después se anuló."
                    + " Si corresponde, cargalo de nuevo.");
        }
    }

    /** Un monto del input para la huella. NaN o infinito no son un pedido: se rechazan acá, con un mensaje. */
    public static BigDecimal monto(Double valor) {
        if (valor == null) return null;
        if (valor.isNaN() || valor.isInfinite()) throw new GraphQLException("Monto inválido.");
        return BigDecimal.valueOf(valor);
    }
}
