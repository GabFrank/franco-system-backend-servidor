package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoProveedor;
import com.franco.dev.domain.financiero.enums.MovimientoProveedorTipo;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.MovimientoProveedorRepository;
import com.franco.dev.repository.personas.ProveedorRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Cuenta corriente del proveedor (ledger de tercero). Unico punto que muta Proveedor.saldoActual. */
@Service
@AllArgsConstructor
public class ProveedorCuentaService {

    private final ProveedorRepository proveedorRepository;
    private final MovimientoProveedorRepository movimientoRepository;

    private static boolean aumentaDeuda(MovimientoProveedorTipo t) {
        return t == MovimientoProveedorTipo.CARGO || t == MovimientoProveedorTipo.AJUSTE_POSITIVO;
    }

    @Transactional
    public MovimientoProveedor registrar(Long proveedorId, MovimientoProveedorTipo tipo, BigDecimal monto,
                                         MovimientoProveedor plantilla, Usuario usuario) {
        Proveedor p = proveedorRepository.lockById(proveedorId)
                .orElseThrow(() -> new GraphQLException("Proveedor no encontrado: " + proveedorId));
        BigDecimal anterior = p.getSaldoActual() != null ? p.getSaldoActual() : BigDecimal.ZERO;
        BigDecimal delta = aumentaDeuda(tipo) ? monto.abs() : monto.abs().negate();
        BigDecimal nuevo = anterior.add(delta);
        p.setSaldoActual(nuevo);
        proveedorRepository.save(p);

        MovimientoProveedor m = plantilla != null ? plantilla : new MovimientoProveedor();
        m.setProveedor(p);
        m.setTipo(tipo);
        m.setMonto(monto.abs());
        m.setSaldoAnterior(anterior);
        m.setSaldoPosterior(nuevo);
        m.setUsuario(usuario);
        m.setAnulado(false);
        return movimientoRepository.save(m);
    }
}
