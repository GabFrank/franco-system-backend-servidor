package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Service
@AllArgsConstructor
public class MovimientoCajaVirtualService {

    private final MovimientoCajaVirtualRepository repository;
    private final CajaVirtualService cajaVirtualService;

    public Optional<MovimientoCajaVirtual> findById(Long id) {
        return repository.findById(id);
    }

    public Page<MovimientoCajaVirtual> findByCajaVirtualId(Long cajaVirtualId, Pageable pageable) {
        return repository.findByCajaVirtualIdOrderByCreadoEnDesc(cajaVirtualId, pageable);
    }

    public Page<MovimientoCajaVirtual> findByCajaVirtualIdAndFecha(Long cajaVirtualId, String inicio, String fin, Pageable pageable) {
        return repository.findByCajaVirtualIdAndCreadoEnBetweenOrderByCreadoEnDesc(
                cajaVirtualId, stringToDate(inicio), stringToDate(fin), pageable);
    }

    /**
     * Registra un movimiento y actualiza el saldo de la caja de forma atómica.
     * Para egresos/salidas, la cantidad llega positiva y se convierte a negativa aquí.
     */
    @Transactional
    public MovimientoCajaVirtual registrarMovimiento(MovimientoCajaVirtual movimiento) {
        if (movimiento.getActivo() == null) movimiento.setActivo(true);

        String monedaDenominacion = movimiento.getMoneda() != null ? movimiento.getMoneda().getDenominacion() : "GUARANI";
        Double cantidad = movimiento.getCantidad();

        // Para egresos, transferencias de salida y pagos, el saldo disminuye
        boolean esEgreso = movimiento.getTipoMovimiento() == CajaVirtualTipoMovimiento.EGRESO
                || movimiento.getTipoMovimiento() == CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA
                || movimiento.getTipoMovimiento() == CajaVirtualTipoMovimiento.PAGO_PROVEEDOR;

        CajaVirtual caja = cajaVirtualService.findById(movimiento.getCajaVirtual().getId())
                .orElseThrow(() -> new GraphQLException("Caja virtual no encontrada"));

        Double saldoAnterior = 0.0;
        if (monedaDenominacion.toUpperCase().contains("GUARANI")) saldoAnterior = caja.getSaldoGs() != null ? caja.getSaldoGs() : 0.0;
        else if (monedaDenominacion.toUpperCase().contains("REAL")) saldoAnterior = caja.getSaldoRs() != null ? caja.getSaldoRs() : 0.0;
        else if (monedaDenominacion.toUpperCase().contains("DOLAR")) saldoAnterior = caja.getSaldoDs() != null ? caja.getSaldoDs() : 0.0;

        movimiento.setSaldoAnterior(saldoAnterior);

        if (esEgreso) {
            // Verificar saldo suficiente antes de operar
            boolean tieneSaldo = cajaVirtualService.verificarSaldo(
                    movimiento.getCajaVirtual().getId(), cantidad, monedaDenominacion);
            if (!tieneSaldo) {
                throw new GraphQLException("Saldo insuficiente en la caja virtual");
            }
            cajaVirtualService.actualizarSaldo(movimiento.getCajaVirtual().getId(), -cantidad, monedaDenominacion);
            movimiento.setSaldoPosterior(saldoAnterior - cantidad);
        } else {
            cajaVirtualService.actualizarSaldo(movimiento.getCajaVirtual().getId(), cantidad, monedaDenominacion);
            movimiento.setSaldoPosterior(saldoAnterior + cantidad);
        }

        return repository.save(movimiento);
    }

    /**
     * Realiza una transferencia entre dos cajas virtuales.
     * Registra TRANSFERENCIA_SALIDA en origen y TRANSFERENCIA_ENTRADA en destino.
     */
    @Transactional
    public Boolean realizarTransferencia(Long origenId, Long destinoId, Double cantidad,
                                         Moneda moneda, String descripcion, Usuario usuario) {
        CajaVirtual origen = cajaVirtualService.findById(origenId)
                .orElseThrow(() -> new GraphQLException("Caja origen no encontrada"));
        CajaVirtual destino = cajaVirtualService.findById(destinoId)
                .orElseThrow(() -> new GraphQLException("Caja destino no encontrada"));

        String monedaDenominacion = moneda != null ? moneda.getDenominacion() : "GUARANI";

        // Verificar saldo en origen
        boolean tieneSaldo = cajaVirtualService.verificarSaldo(origenId, cantidad, monedaDenominacion);
        if (!tieneSaldo) {
            throw new GraphQLException("Saldo insuficiente en caja origen para realizar la transferencia");
        }

        // Movimiento de salida en origen
        MovimientoCajaVirtual salida = new MovimientoCajaVirtual();
        salida.setCajaVirtual(origen);
        salida.setTipoMovimiento(CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA);
        salida.setCantidad(cantidad);
        salida.setMoneda(moneda);
        salida.setCajaOrigen(origen);
        salida.setCajaDestino(destino);
        salida.setDescripcion(descripcion);
        salida.setUsuario(usuario);
        salida.setActivo(true);
        registrarMovimiento(salida);

        // Movimiento de entrada en destino
        MovimientoCajaVirtual entrada = new MovimientoCajaVirtual();
        entrada.setCajaVirtual(destino);
        entrada.setTipoMovimiento(CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA);
        entrada.setCantidad(cantidad);
        entrada.setMoneda(moneda);
        entrada.setCajaOrigen(origen);
        entrada.setCajaDestino(destino);
        entrada.setDescripcion(descripcion);
        entrada.setUsuario(usuario);
        entrada.setActivo(true);
        registrarMovimiento(entrada);

        return true;
    }
}
