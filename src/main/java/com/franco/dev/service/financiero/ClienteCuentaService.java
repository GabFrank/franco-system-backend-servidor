package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoCliente;
import com.franco.dev.domain.financiero.enums.MovimientoClienteTipo;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.MovimientoClienteRepository;
import com.franco.dev.repository.personas.ClienteRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Cuenta corriente del cliente (ledger de tercero). Es el único punto que muta
 * {@code Cliente.saldoActual} (con lock). CARGO aumenta la deuda, PAGO la reduce.
 */
@Service
@AllArgsConstructor
public class ClienteCuentaService {

    private final ClienteRepository clienteRepository;
    private final MovimientoClienteRepository movimientoRepository;

    private static boolean aumentaDeuda(MovimientoClienteTipo t) {
        return t == MovimientoClienteTipo.CARGO || t == MovimientoClienteTipo.AJUSTE_POSITIVO;
    }

    /**
     * Registra un movimiento en el libro del cliente y ajusta su saldo (deuda).
     * Devuelve el movimiento con los vínculos cruzados que el caller quiera setear luego.
     */
    @Transactional
    public MovimientoCliente registrar(Long clienteId, MovimientoClienteTipo tipo, BigDecimal monto,
                                       MovimientoCliente plantilla, Usuario usuario) {
        Cliente cliente = clienteRepository.lockById(clienteId)
                .orElseThrow(() -> new GraphQLException("Cliente no encontrado: " + clienteId));
        BigDecimal anterior = cliente.getSaldoActual() != null ? cliente.getSaldoActual() : BigDecimal.ZERO;
        BigDecimal delta = aumentaDeuda(tipo) ? monto.abs() : monto.abs().negate();
        BigDecimal nuevo = anterior.add(delta);
        cliente.setSaldoActual(nuevo);
        clienteRepository.save(cliente);

        MovimientoCliente m = plantilla != null ? plantilla : new MovimientoCliente();
        m.setCliente(cliente);
        m.setTipo(tipo);
        m.setMonto(monto.abs());
        m.setSaldoAnterior(anterior);
        m.setSaldoPosterior(nuevo);
        m.setUsuario(usuario);
        m.setAnulado(false);
        return movimientoRepository.save(m);
    }
}
