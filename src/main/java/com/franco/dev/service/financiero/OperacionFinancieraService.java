package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.OperacionFinanciera;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.financiero.enums.TipoOperacionFinanciera;
import com.franco.dev.domain.personas.Usuario;
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
                cajaMov(op.getCajaMayorOrigen(), CajaVirtualTipoMovimiento.EGRESO, op.getMontoOrigen(), op.getMonedaOrigen(), saved, usuario);
                cajaMov(op.getCajaMayorDestino(), CajaVirtualTipoMovimiento.INGRESO, op.getMontoDestino(), op.getMonedaDestino(), saved, usuario);
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
                cajaMov(op.getCajaMayorOrigen(), CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA, op.getMontoOrigen(), op.getMonedaOrigen(), saved, usuario);
                cajaMov(op.getCajaMayorDestino(), CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA, op.getMontoDestino(), op.getMonedaDestino(), saved, usuario);
                break;
            case TRANSFERENCIA_BANCARIA:
                req(op.getCuentaBancariaOrigen() != null && op.getCuentaBancariaDestino() != null,
                        "Transferencia bancaria requiere cuenta origen y destino");
                req(!op.getCuentaBancariaOrigen().getId().equals(op.getCuentaBancariaDestino().getId()),
                        "La cuenta origen y destino no pueden ser la misma");
                // No toca caja mayor.
                bancoLedgerService.registrar(op.getCuentaBancariaOrigen().getId(), MovimientoBancarioTipo.SALIDA_MANUAL,
                        montoOrigen(op), "Transferencia bancaria (op #" + saved.getId() + ")",
                        OrigenMovimientoTipo.OPERACION_FINANCIERA.name(), saved.getId(), usuario);
                bancoLedgerService.registrar(op.getCuentaBancariaDestino().getId(), MovimientoBancarioTipo.ENTRADA_MANUAL,
                        montoDestino(op), "Transferencia bancaria (op #" + saved.getId() + ")",
                        OrigenMovimientoTipo.OPERACION_FINANCIERA.name(), saved.getId(), usuario);
                break;
            default:
                throw new GraphQLException("Tipo de operación no soportado: " + t);
        }
        return saved;
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
