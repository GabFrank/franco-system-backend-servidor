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
        op.setUsuario(usuario);
        op.setAnulado(false);
        OperacionFinanciera saved = repository.save(op);

        TipoOperacionFinanciera t = op.getTipoOperacion();
        switch (t) {
            case CAMBIO_DIVISA:
                req(op.getCajaMayorOrigen() != null && op.getCajaMayorDestino() != null,
                        "Cambio de divisa requiere caja origen y destino");
                // Orden canónico de lock (id de caja ascendente) para evitar deadlock A→B / B→A.
                if (op.getCajaMayorOrigen().getId() <= op.getCajaMayorDestino().getId()) {
                    cajaMov(op.getCajaMayorOrigen(), CajaVirtualTipoMovimiento.EGRESO, op.getMontoOrigen(), op.getMonedaOrigen(), saved, usuario);
                    cajaMov(op.getCajaMayorDestino(), CajaVirtualTipoMovimiento.INGRESO, op.getMontoDestino(), op.getMonedaDestino(), saved, usuario);
                } else {
                    cajaMov(op.getCajaMayorDestino(), CajaVirtualTipoMovimiento.INGRESO, op.getMontoDestino(), op.getMonedaDestino(), saved, usuario);
                    cajaMov(op.getCajaMayorOrigen(), CajaVirtualTipoMovimiento.EGRESO, op.getMontoOrigen(), op.getMonedaOrigen(), saved, usuario);
                }
                break;
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
                bancoLedgerService.registrar(op.getCuentaBancariaOrigen().getId(), MovimientoBancarioTipo.SALIDA_MANUAL,
                        montoOrigen(op), "Retiro bancario (op #" + saved.getId() + ")",
                        OrigenMovimientoTipo.OPERACION_FINANCIERA.name(), saved.getId(), usuario);
                cajaMov(op.getCajaMayorDestino(), CajaVirtualTipoMovimiento.INGRESO, op.getMontoDestino(), op.getMonedaDestino(), saved, usuario);
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

    /**
     * Anula una operación financiera completa desde la caja mayor: revierte TODAS sus patas
     * (ambas patas de caja de un cambio de divisa / transferencia entre cajas, la pata de caja
     * + la pata bancaria de un depósito/retiro, las dos patas bancarias de una transferencia
     * bancaria, y el AJUSTE de diferencia si lo hubo) de forma atómica. Cada pata se revierte
     * con su contra-movimiento (ledger inmutable). Idempotente por el flag {@code anulado}.
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
    private void aplicarDiferencia(OperacionFinanciera op, OperacionFinanciera saved, Usuario usuario) {
        // TRANSFERENCIA_BANCARIA no toca caja mayor: no hay dónde imputar la diferencia. Se ignora.
        if (op.getTipoOperacion() == TipoOperacionFinanciera.TRANSFERENCIA_BANCARIA) return;

        com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo destino = op.getDiferenciaDestinoTipo();
        BigDecimal dif = op.getDiferencia();
        if (dif == null || dif.signum() == 0) return;
        if (destino == null || destino == com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo.IGNORAR) return;

        CajaVirtual caja = op.getCajaMayorDestino() != null ? op.getCajaMayorDestino() : op.getCajaMayorOrigen();
        Moneda moneda = op.getCajaMayorDestino() != null ? op.getMonedaDestino() : op.getMonedaOrigen();
        req(caja != null, "La diferencia requiere una caja mayor donde imputarse");

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
