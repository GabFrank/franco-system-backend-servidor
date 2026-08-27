package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.OperacionFinanciera;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.financiero.enums.TipoOperacionFinanciera;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.OperacionFinancieraRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Operaciones financieras: los 5 tipos (cambio divisa, depósito/retiro banco,
 * transferencia entre cajas, transferencia bancaria). Cada una postea los
 * movimientos de caja mayor (via {@link TesoreriaService}) y/o de banco (via
 * {@link BancoLedgerService}) que correspondan, de forma atómica.
 */
@Service
@AllArgsConstructor
public class OperacionFinancieraService {

    private final OperacionFinancieraRepository repository;
    private final TesoreriaService tesoreriaService;
    private final BancoLedgerService bancoLedgerService;
    private final MovimientoCajaVirtualRepository movimientoCajaVirtualRepository;
    private final MovimientoBancarioRepository movimientoBancarioRepository;

    public Page<OperacionFinanciera> findAll(Pageable pageable) {
        return repository.findAllByOrderByCreadoEnDesc(pageable);
    }

    private void req(boolean cond, String msg) { if (!cond) throw new GraphQLException(msg); }

    @Transactional
    public OperacionFinanciera registrar(OperacionFinanciera op, Usuario usuario) {
        req(op.getTipoOperacion() != null, "Tipo de operación requerido");
        derivarMonedasDeCuentas(op);
        validarDestinoDeDiferencia(op);
        op.setUsuario(usuario);
        op.setAnulado(false);
        OperacionFinanciera saved = repository.save(op);

        TipoOperacionFinanciera t = op.getTipoOperacion();
        switch (t) {
            case CAMBIO_DIVISA: {
                // Cada lado puede ser una caja mayor o una cuenta bancaria: los cambios entre
                // cuentas son tan comunes como los de mostrador, y antes había que registrarlos
                // como dos operaciones sueltas, perdiendo la relación entre las dos patas.
                boolean cajaOrig = op.getCajaMayorOrigen() != null;
                boolean bancoOrig = op.getCuentaBancariaOrigen() != null;
                boolean cajaDest = op.getCajaMayorDestino() != null;
                boolean bancoDest = op.getCuentaBancariaDestino() != null;
                req(cajaOrig ^ bancoOrig, "Elegí una caja mayor o una cuenta bancaria de origen, no las dos");
                req(cajaDest ^ bancoDest, "Elegí una caja mayor o una cuenta bancaria de destino, no las dos");
                req(!(bancoOrig && bancoDest
                                && op.getCuentaBancariaOrigen().getId().equals(op.getCuentaBancariaDestino().getId())),
                        "La cuenta de origen y la de destino no pueden ser la misma");

                Runnable salida = () -> {
                    if (cajaOrig) {
                        cajaMov(op.getCajaMayorOrigen(), CajaVirtualTipoMovimiento.EGRESO,
                                montoOrigen(op), op.getMonedaOrigen(), saved, usuario);
                    } else {
                        bancoLedgerService.registrar(op.getCuentaBancariaOrigen().getId(),
                                MovimientoBancarioTipo.SALIDA_MANUAL, montoOrigen(op),
                                "Cambio de divisa (op #" + saved.getId() + ")",
                                OrigenMovimientoTipo.OPERACION_FINANCIERA.name(), saved.getId(), usuario);
                    }
                };
                Runnable entrada = () -> {
                    if (cajaDest) {
                        cajaMov(op.getCajaMayorDestino(), CajaVirtualTipoMovimiento.INGRESO,
                                montoDestino(op), op.getMonedaDestino(), saved, usuario);
                    } else {
                        bancoLedgerService.registrar(op.getCuentaBancariaDestino().getId(),
                                MovimientoBancarioTipo.ENTRADA_MANUAL, montoDestino(op),
                                "Cambio de divisa (op #" + saved.getId() + ")",
                                OrigenMovimientoTipo.OPERACION_FINANCIERA.name(), saved.getId(), usuario);
                    }
                };
                ejecutarEnOrden(
                        new Pata(salida, cajaOrig ? op.getCajaMayorOrigen().getId() : null,
                                bancoOrig ? op.getCuentaBancariaOrigen().getId() : null),
                        new Pata(entrada, cajaDest ? op.getCajaMayorDestino().getId() : null,
                                bancoDest ? op.getCuentaBancariaDestino().getId() : null));
                break;
            }
            case DEPOSITO_BANCARIO:
                req(op.getCajaMayorOrigen() != null && op.getCuentaBancariaDestino() != null,
                        "Depósito requiere caja origen y cuenta bancaria destino");
                cajaMov(op.getCajaMayorOrigen(), CajaVirtualTipoMovimiento.EGRESO, op.getMontoOrigen(), op.getMonedaOrigen(), saved, usuario);
                bancoLedgerService.registrar(op.getCuentaBancariaDestino().getId(), MovimientoBancarioTipo.ENTRADA_MANUAL,
                        montoDestino(op), "Depósito bancario (op #" + saved.getId() + ")",
                        OrigenMovimientoTipo.OPERACION_FINANCIERA.name(), saved.getId(), usuario);
                break;
            case RETIRO_BANCARIO:
                req(op.getCuentaBancariaOrigen() != null && op.getCajaMayorDestino() != null,
                        "Retiro bancario requiere cuenta origen y caja destino");
                // Caja ANTES que banco, igual que el depósito. Antes era al revés, y con el
                // cambio de divisa mixto tocando estos mismos recursos ese orden inverso
                // alcanzaba para trabar un retiro contra un cambio simultáneo: los dos locks
                // son SELECT FOR UPDATE, que espera, no falla rápido.
                cajaMov(op.getCajaMayorDestino(), CajaVirtualTipoMovimiento.INGRESO, op.getMontoDestino(), op.getMonedaDestino(), saved, usuario);
                bancoLedgerService.registrar(op.getCuentaBancariaOrigen().getId(), MovimientoBancarioTipo.SALIDA_MANUAL,
                        montoOrigen(op), "Retiro bancario (op #" + saved.getId() + ")",
                        OrigenMovimientoTipo.OPERACION_FINANCIERA.name(), saved.getId(), usuario);
                break;
            case TRANSFERENCIA_ENTRE_CAJAS:
                req(op.getCajaMayorOrigen() != null && op.getCajaMayorDestino() != null,
                        "Transferencia entre cajas requiere origen y destino");
                // Orden canónico de lock (id de caja ascendente) para evitar deadlock.
                if (op.getCajaMayorOrigen().getId() <= op.getCajaMayorDestino().getId()) {
                    cajaMov(op.getCajaMayorOrigen(), CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA, op.getMontoOrigen(), op.getMonedaOrigen(), saved, usuario);
                    cajaMov(op.getCajaMayorDestino(), CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA, op.getMontoDestino(), op.getMonedaDestino(), saved, usuario);
                } else {
                    cajaMov(op.getCajaMayorDestino(), CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA, op.getMontoDestino(), op.getMonedaDestino(), saved, usuario);
                    cajaMov(op.getCajaMayorOrigen(), CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA, op.getMontoOrigen(), op.getMonedaOrigen(), saved, usuario);
                }
                break;
            case TRANSFERENCIA_BANCARIA:
                req(op.getCuentaBancariaOrigen() != null && op.getCuentaBancariaDestino() != null,
                        "Transferencia bancaria requiere cuenta origen y destino");
                req(!op.getCuentaBancariaOrigen().getId().equals(op.getCuentaBancariaDestino().getId()),
                        "La cuenta origen y destino no pueden ser la misma");
                // No toca caja mayor. Orden canónico de lock (id de cuenta ascendente) para evitar deadlock.
                Runnable salida = () -> bancoLedgerService.registrar(op.getCuentaBancariaOrigen().getId(), MovimientoBancarioTipo.SALIDA_MANUAL,
                        montoOrigen(op), "Transferencia bancaria (op #" + saved.getId() + ")",
                        OrigenMovimientoTipo.OPERACION_FINANCIERA.name(), saved.getId(), usuario);
                Runnable entrada = () -> bancoLedgerService.registrar(op.getCuentaBancariaDestino().getId(), MovimientoBancarioTipo.ENTRADA_MANUAL,
                        montoDestino(op), "Transferencia bancaria (op #" + saved.getId() + ")",
                        OrigenMovimientoTipo.OPERACION_FINANCIERA.name(), saved.getId(), usuario);
                if (op.getCuentaBancariaOrigen().getId() <= op.getCuentaBancariaDestino().getId()) { salida.run(); entrada.run(); }
                else { entrada.run(); salida.run(); }
                break;
            default:
                throw new GraphQLException("Tipo de operación no soportado: " + t);
        }

        aplicarDiferencia(op, saved, usuario);
        return saved;
    }

    /** Trae una operación financiera por id (para el detalle read-only en caja mayor). */
    @Transactional(readOnly = true)
    public OperacionFinanciera porId(Long id) {
        return id != null ? repository.findById(id).orElse(null) : null;
    }

    /**
     * Anula una operación financiera completa desde la caja mayor: revierte TODAS sus patas
     * (ambas patas de caja de un cambio de divisa / transferencia entre cajas, la pata de caja
     * + la pata bancaria de un depósito/retiro, las dos patas bancarias de una transferencia
     * bancaria, y el AJUSTE de diferencia si lo hubo) de forma atómica. Cada pata se revierte
     * con su contra-movimiento (ledger inmutable). Idempotente por el flag {@code anulado}.
     *
     * El @Transactional estaba sobre porId(): alguien insertó ese método entre este javadoc y
     * su firma y se llevó la anotación puesta. Sin transacción, cada revertir() commiteaba por
     * su cuenta y una falla a mitad de camino —un descubierto al revertir una entrada, por
     * ejemplo— dejaba la operación con una pata movida y el flag `anulado` todavía en false.
     */
    @Transactional
    public OperacionFinanciera anular(Long operacionId, String motivo, Usuario usuario) {
        OperacionFinanciera op = repository.findById(operacionId)
                .orElseThrow(() -> new GraphQLException("Operación financiera no encontrada: " + operacionId));
        if (Boolean.TRUE.equals(op.getAnulado())) {
            throw new GraphQLException("La operación financiera #" + operacionId + " ya está anulada");
        }
        String razon = (motivo != null && !motivo.trim().isEmpty())
                ? motivo : "Anulación operación financiera #" + operacionId;

        // Patas de caja mayor (cambio divisa, transf. entre cajas, pata de caja de depósito/retiro, AJUSTE de diferencia).
        for (MovimientoCajaVirtual m : movimientoCajaVirtualRepository
                .findByOrigenTipoAndOrigenIdAndActivoTrue(OrigenMovimientoTipo.OPERACION_FINANCIERA, operacionId)) {
            tesoreriaService.revertir(m, razon, usuario);
        }
        // Patas bancarias (depósito, retiro, transferencia bancaria).
        for (MovimientoBancario m : movimientoBancarioRepository
                .findByOrigenTipoAndOrigenIdAndAnuladoFalse(OrigenMovimientoTipo.OPERACION_FINANCIERA.name(), operacionId)) {
            bancoLedgerService.revertir(m, razon, usuario);
        }

        op.setAnulado(true);
        return repository.save(op);
    }

    /**
     * Imputa la diferencia (sobra/falta) como un AJUSTE etiquetado en la caja mayor de destino
     * (o la de origen si no hay destino). No aplica a TRANSFERENCIA_BANCARIA (no toca caja).
     * Igual que gourmet: no crea un Gasto/Vale real, solo un ajuste de caja rotulado.
     */
    /** true si la operación trae una diferencia que hay que imputar en alguna caja mayor. */
    private boolean pideImputarDiferencia(OperacionFinanciera op) {
        BigDecimal dif = op.getDiferencia();
        if (dif == null || dif.signum() == 0) return false;
        com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo destino = op.getDiferenciaDestinoTipo();
        return destino != null
                && destino != com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo.IGNORAR;
    }

    /**
     * La diferencia se imputa como un AJUSTE en una caja mayor, así que si la operación no toca
     * ninguna —una transferencia bancaria, o un cambio de divisa entre dos cuentas— no hay dónde
     * ponerla. Se valida antes de postear: fallar después deja el rollback correcto pero un
     * mensaje que llega cuando el usuario ya cree que grabó.
     *
     * El criterio es "hay caja mayor", no el tipo de operación: desde que el cambio de divisa
     * puede ser banco a banco, atarlo al enum dejaba ese caso afuera del control.
     */
    private void validarDestinoDeDiferencia(OperacionFinanciera op) {
        if (!pideImputarDiferencia(op)) return;
        req(op.getCajaMayorOrigen() != null || op.getCajaMayorDestino() != null,
                "Esta operación no toca ninguna caja mayor: la diferencia no tiene dónde imputarse");
    }

    private void aplicarDiferencia(OperacionFinanciera op, OperacionFinanciera saved, Usuario usuario) {
        if (!pideImputarDiferencia(op)) return;

        CajaVirtual caja = op.getCajaMayorDestino() != null ? op.getCajaMayorDestino() : op.getCajaMayorOrigen();
        Moneda moneda = op.getCajaMayorDestino() != null ? op.getMonedaDestino() : op.getMonedaOrigen();
        BigDecimal dif = op.getDiferencia();
        com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo destino = op.getDiferenciaDestinoTipo();

        MovimientoCajaVirtual m = new MovimientoCajaVirtual();
        m.setCajaVirtual(caja);
        m.setTipoMovimiento(CajaVirtualTipoMovimiento.AJUSTE);
        m.setCantidad(dif.doubleValue()); // AJUSTE conserva el signo (positivo=sobra, negativo=falta)
        m.setMoneda(moneda);
        m.setUsuario(usuario);
        String rotulo = destino + " POR DIFERENCIA (op #" + saved.getId() + ")"
                + (op.getDiferenciaObservacion() != null ? " - " + op.getDiferenciaObservacion() : "");
        m.setDescripcion(rotulo);
        m.setReferenciaId(saved.getId());
        m.setOrigenTipo(OrigenMovimientoTipo.OPERACION_FINANCIERA);
        m.setOrigenId(saved.getId());
        tesoreriaService.registrar(m);
    }

    /**
     * Una pata del asiento junto con los recursos que va a lockear.
     *
     * Solo uno de los dos ids está seteado: la pata toca una caja mayor o una cuenta bancaria.
     */
    private static final class Pata {
        final Runnable accion;
        final Long cajaId;
        final Long cuentaId;

        Pata(Runnable accion, Long cajaId, Long cuentaId) {
            this.accion = accion;
            this.cajaId = cajaId;
            this.cuentaId = cuentaId;
        }

        /**
         * Clave de ordenamiento: primero todas las cajas (por id), después todas las cuentas
         * (por id). Los ids de caja y de cuenta viven en tablas distintas, así que compararlos
         * entre sí no significa nada — hace falta separarlos por tipo primero.
         */
        long[] clave() {
            return cajaId != null ? new long[]{0, cajaId} : new long[]{1, cuentaId != null ? cuentaId : 0};
        }
    }

    /**
     * Ejecuta las dos patas en orden canónico: cajas antes que bancos, y dentro de cada grupo
     * por id ascendente.
     *
     * Sin un orden único y global, dos operaciones simultáneas que tocan los mismos dos
     * recursos en sentido contrario se traban entre sí. No alcanza con ordenar dentro de una
     * operación: el orden tiene que ser el mismo en todos los flujos que lockean caja y banco.
     */
    private void ejecutarEnOrden(Pata a, Pata b) {
        long[] ka = a.clave(), kb = b.clave();
        boolean primeroA = ka[0] != kb[0] ? ka[0] < kb[0] : ka[1] <= kb[1];
        if (primeroA) { a.accion.run(); b.accion.run(); }
        else { b.accion.run(); a.accion.run(); }
    }

    /**
     * La moneda de una pata bancaria la manda la cuenta, no el formulario.
     *
     * {@code MovimientoBancario} no guarda moneda — la de la cuenta es la única que existe — así
     * que el campo de la operación es exhibición. Derivarlo en vez de validarlo evita rechazar
     * operaciones que hoy funcionan con el campo en null, y hace imposible que quede exhibida
     * una moneda distinta de la real.
     */
    private void derivarMonedasDeCuentas(OperacionFinanciera op) {
        if (op.getCuentaBancariaOrigen() != null && op.getCuentaBancariaOrigen().getMoneda() != null) {
            op.setMonedaOrigen(op.getCuentaBancariaOrigen().getMoneda());
        }
        if (op.getCuentaBancariaDestino() != null && op.getCuentaBancariaDestino().getMoneda() != null) {
            op.setMonedaDestino(op.getCuentaBancariaDestino().getMoneda());
        }
    }

    private BigDecimal montoOrigen(OperacionFinanciera op) {
        req(op.getMontoOrigen() != null && op.getMontoOrigen().signum() > 0, "Monto origen inválido");
        return op.getMontoOrigen();
    }

    private BigDecimal montoDestino(OperacionFinanciera op) {
        req(op.getMontoDestino() != null && op.getMontoDestino().signum() > 0, "Monto destino inválido");
        return op.getMontoDestino();
    }

    private void cajaMov(CajaVirtual caja, CajaVirtualTipoMovimiento tipo, BigDecimal monto, Moneda moneda,
                         OperacionFinanciera op, Usuario usuario) {
        req(monto != null && monto.signum() > 0, "Monto inválido en operación financiera");
        MovimientoCajaVirtual m = new MovimientoCajaVirtual();
        m.setCajaVirtual(caja);
        m.setTipoMovimiento(tipo);
        m.setCantidad(monto.doubleValue());
        m.setMoneda(moneda);
        m.setUsuario(usuario);
        m.setDescripcion(op.getTipoOperacion() + " (op #" + op.getId() + ")");
        m.setReferenciaId(op.getId());
        m.setOrigenTipo(OrigenMovimientoTipo.OPERACION_FINANCIERA);
        m.setOrigenId(op.getId());
        tesoreriaService.registrar(m);
    }
}
