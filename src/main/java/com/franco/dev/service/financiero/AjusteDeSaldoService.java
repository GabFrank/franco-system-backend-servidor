package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Ajustes de saldo que dicen contra qué saldo se hicieron (issue #376).
 *
 * <p>El ajuste por conteo de una caja y el ajuste de saldo de una cuenta bancaria se calculaban en el
 * desktop con el saldo que tenía en pantalla, y el central aplicaba la diferencia sobre el saldo de
 * ese momento: si entre medio entraba otro movimiento, o dos personas ajustaban lo mismo, quedaba un
 * saldo que nadie había contado.</p>
 *
 * <p>En los dos casos: saldo con lock → <b>refrescar la entidad</b> → comparar → registrar. El refresh
 * no es opcional: el lock devuelve la instancia que la request ya tuviera cargada, y
 * {@code registrar} calcula con ella; comparando contra una proyección, la comprobación y el registro
 * podrían mirar saldos distintos.</p>
 */
@Service
@RequiredArgsConstructor
public class AjusteDeSaldoService {

    static final String OPERACION_AJUSTE_BANCARIO = "AJUSTE_SALDO_BANCARIO";
    /** Los saldos son numeric(18,4). */
    private static final int DECIMALES = 4;

    private final TesoreriaService tesoreriaService;
    private final BancoLedgerService bancoLedgerService;
    private final TesoreriaSecurityService seguridad;
    private final IdempotenciaService idempotenciaService;
    private final CajaVirtualRepository cajaVirtualRepository;
    private final CajaVirtualSaldoRepository saldoRepository;
    private final MonedaRepository monedaRepository;
    private final CuentaBancariaRepository cuentaRepository;
    private final MovimientoBancarioRepository movimientoBancarioRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /** Para los tests unitarios, que arman el servicio a mano. */
    void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Deja el saldo de {@code (caja, moneda)} en lo contado. La diferencia la calcula el central, con el
     * saldo tomado con lock: el desktop solo dice qué saldo vio y cuánto contó.
     *
     * <p>No lleva clave de idempotencia porque es absoluto: repetido después de aplicarse, el saldo ya
     * coincide con lo contado (o cambió) y se rechaza; y si el saldo volviera justo al esperado,
     * aplicarlo de nuevo lo deja otra vez en lo contado, que es lo que se pidió.</p>
     */
    @Transactional
    public MovimientoCajaVirtual ajustarCajaPorConteo(Long cajaVirtualId, Long monedaId, Double saldoEsperado,
                                                      Double contado, Usuario usuario) {
        if (cajaVirtualId == null || monedaId == null) throw new GraphQLException("Debe indicar la caja y la moneda.");
        if (!finito(saldoEsperado) || !finito(contado)) throw new GraphQLException("Monto inválido.");
        // Se redondea, no se rechaza: lo contado es una suma hecha en el cliente (12.350000000000001).
        BigDecimal contadoExacto = BigDecimal.valueOf(contado).setScale(DECIMALES, RoundingMode.HALF_UP);
        if (contadoExacto.signum() < 0) throw new GraphQLException("Lo contado no puede ser negativo.");

        Moneda moneda = monedaRepository.findById(monedaId)
                .orElseThrow(() -> new GraphQLException("Moneda no encontrada: " + monedaId));
        seguridad.requireEscrituraCaja(cajaVirtualId);
        if (!cajaVirtualRepository.existsById(cajaVirtualId)) {
            throw new GraphQLException("Caja virtual no encontrada: " + cajaVirtualId);
        }

        saldoRepository.ensureRow(cajaVirtualId, monedaId);
        CajaVirtualSaldo saldo = saldoRepository.lockByCajaVirtualIdAndMonedaId(cajaVirtualId, monedaId)
                .orElseThrow(() -> new GraphQLException("No se pudo tomar el saldo de la caja"));
        entityManager.refresh(saldo);
        BigDecimal actual = saldo.getSaldo() != null ? saldo.getSaldo() : BigDecimal.ZERO;

        // Primero «ya coincide»: es lo que tiene que leer quien reintenta un ajuste que sí había entrado.
        if (actual.compareTo(contadoExacto) == 0) {
            throw new GraphQLException("El saldo de la caja ya coincide con lo contado: no hace falta ajustar.");
        }
        if (!coincide(actual, saldoEsperado)) {
            throw new GraphQLException("El saldo de la caja cambió desde que abriste el conteo (era "
                    + fmt(BigDecimal.valueOf(saldoEsperado)) + ", ahora es " + fmt(actual)
                    + "). Volvé a abrirlo: lo contado no se pierde.");
        }

        // La fila de la caja se lee recién ahora, con el saldo ya tomado, y se refresca: el chequeo de
        // permiso pudo haberla cargado antes, y el shim saldo_gs/rs/ds se guarda con la fila entera.
        CajaVirtual caja = cajaVirtualRepository.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja virtual no encontrada: " + cajaVirtualId));
        entityManager.refresh(caja);

        MovimientoCajaVirtual ajuste = new MovimientoCajaVirtual();
        ajuste.setCajaVirtual(caja);
        ajuste.setTipoMovimiento(CajaVirtualTipoMovimiento.AJUSTE);
        ajuste.setCantidad(contadoExacto.subtract(actual).doubleValue());   // con signo: negativo si falta plata
        ajuste.setMoneda(moneda);
        ajuste.setUsuario(usuario);
        ajuste.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
        ajuste.setActivo(true);
        ajuste.setDescripcion("AJUSTE POR CONTEO DE CAJA (SISTEMA " + fmt(actual) + " / CONTADO " + fmt(contadoExacto) + ")");
        return tesoreriaService.registrar(ajuste);
    }

    /**
     * Suma o resta {@code monto} al saldo de una cuenta bancaria.
     *
     * <p>Es relativo, y por eso lleva dos resguardos distintos. {@code saldoEsperado} cubre el saldo
     * viejo en pantalla y a dos personas ajustando la misma cuenta. {@code claveIdempotencia} cubre el
     * reintento: el saldo esperado solo no alcanza, porque si después del ajuste entra un movimiento
     * opuesto por el mismo monto el saldo vuelve al esperado, y repetir el pedido lo aplicaría otra
     * vez. Los dos son opcionales: sin ellos (un desktop anterior) ajusta como siempre.</p>
     */
    @Transactional
    public MovimientoBancario ajustarSaldoBancario(Long cuentaBancariaId, Double monto, Boolean positivo, String motivo,
                                                   Double saldoEsperado, String claveIdempotencia, Usuario usuario) {
        if (cuentaBancariaId == null) throw new GraphQLException("Debe indicar la cuenta.");
        if (!finito(monto) || monto <= 0) throw new GraphQLException("El monto del ajuste debe ser mayor a cero");
        if (motivo == null || motivo.trim().isEmpty()) throw new GraphQLException("El motivo del ajuste es obligatorio");
        if (saldoEsperado != null && !finito(saldoEsperado)) throw new GraphQLException("Monto inválido.");
        BigDecimal montoExacto = BigDecimal.valueOf(monto);
        String motivoLimpio = motivo.trim().toUpperCase();
        MovimientoBancarioTipo tipo = Boolean.TRUE.equals(positivo)
                ? MovimientoBancarioTipo.AJUSTE_POSITIVO : MovimientoBancarioTipo.AJUSTE_NEGATIVO;

        String huella = new HuellaPedido().id(cuentaBancariaId).numero(montoExacto).bandera(positivo)
                .texto(motivoLimpio).numero(saldoEsperado != null ? BigDecimal.valueOf(saldoEsperado) : null).calcular();

        return idempotenciaService.ejecutar(claveIdempotencia, OPERACION_AJUSTE_BANCARIO, huella, usuario,
                () -> {
                    if (saldoEsperado != null) {
                        CuentaBancaria cuenta = cuentaRepository.lockById(cuentaBancariaId)
                                .orElseThrow(() -> new GraphQLException("Cuenta bancaria no encontrada: " + cuentaBancariaId));
                        entityManager.refresh(cuenta);
                        BigDecimal actual = cuenta.getSaldo() != null ? cuenta.getSaldo() : BigDecimal.ZERO;
                        if (!coincide(actual, saldoEsperado)) {
                            throw new GraphQLException("El saldo de la cuenta cambió (era "
                                    + fmt(BigDecimal.valueOf(saldoEsperado)) + ", ahora es " + fmt(actual)
                                    + "). Revisá sus movimientos antes de ajustar: si venías de un ajuste"
                                    + " sin confirmar, puede que ya haya entrado.");
                        }
                    }
                    return bancoLedgerService.registrar(cuentaBancariaId, tipo, montoExacto,
                            "AJUSTE: " + motivoLimpio, OrigenMovimientoTipo.MANUAL.name(), null, usuario);
                },
                MovimientoBancario::getId,
                this::ajusteYaAplicado);
    }

    /** El ajuste del pedido original. Si después se anuló, se rechaza: ni éxito ni otro ajuste (§7.1). */
    private MovimientoBancario ajusteYaAplicado(Long movimientoId) {
        MovimientoBancario movimiento = movimientoBancarioRepository.findById(movimientoId).orElse(null);
        if (movimiento != null && movimientoBancarioRepository.findAnuladoById(movimientoId)
                .orElse(Boolean.TRUE.equals(movimiento.getAnulado()))) {
            throw new GraphQLException("Este ajuste ya se aplicó y después fue anulado."
                    + " Revisá los movimientos de la cuenta antes de repetirlo.");
        }
        return movimiento;
    }

    private static boolean finito(Double valor) {
        return valor != null && !valor.isNaN() && !valor.isInfinite();
    }

    /**
     * ¿El saldo que mandó el cliente es el actual? Igual a 4 decimales, o igual a su valor en Double:
     * es el camino por el que el cliente lo recibió, y en saldos muy grandes el Double no guarda los 4
     * decimales.
     */
    private static boolean coincide(BigDecimal actual, Double esperado) {
        BigDecimal esperadoExacto = BigDecimal.valueOf(esperado).setScale(DECIMALES, RoundingMode.HALF_UP);
        return actual.setScale(DECIMALES, RoundingMode.HALF_UP).compareTo(esperadoExacto) == 0
                || actual.doubleValue() == esperado;
    }

    /** 1.234.567,89: como se leen los montos en pantalla. */
    private static String fmt(BigDecimal valor) {
        return new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(new Locale("es", "PY"))).format(valor);
    }
}
