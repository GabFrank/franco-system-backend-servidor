package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EntradaVaria;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.EntradaVariaRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Entradas/salidas varias de caja mayor: ingreso o egreso manual de efectivo
 * clasificado por categoría. Cada alta postea un {@code MovimientoCajaVirtual}
 * vía {@link TesoreriaService}; la anulación genera el contra-movimiento.
 */
@Service
@AllArgsConstructor
public class EntradaVariaService {

    private final EntradaVariaRepository repository;
    private final TesoreriaService tesoreriaService;
    private final ComprobanteSerieService comprobanteSerieService;

    public Optional<EntradaVaria> findById(Long id) { return repository.findById(id); }

    public Page<EntradaVaria> findByCaja(Long cajaVirtualId, Pageable pageable) {
        return repository.findByCajaVirtualIdOrderByCreadoEnDesc(cajaVirtualId, pageable);
    }

    /** Registra la entrada/salida varia y su movimiento de caja. */
    @Transactional
    public EntradaVaria registrar(EntradaVaria e, Usuario usuario) {
        if (e.getMonto() == null || e.getMonto().signum() <= 0) {
            throw new GraphQLException("El monto de la entrada/salida debe ser positivo");
        }
        if (e.getCajaVirtual() == null) {
            throw new GraphQLException("Debe indicar la caja mayor");
        }
        boolean esIngreso = Boolean.TRUE.equals(e.getEsIngreso());
        e.setUsuario(usuario);
        e.setAnulado(false);
        if (e.getNumeroComprobante() == null) {
            e.setNumeroComprobante(comprobanteSerieService.siguienteNumero("ENTRADA_VARIA"));
        }
        EntradaVaria saved = repository.save(e);

        MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
        mov.setCajaVirtual(e.getCajaVirtual());
        mov.setTipoMovimiento(esIngreso ? CajaVirtualTipoMovimiento.INGRESO : CajaVirtualTipoMovimiento.EGRESO);
        mov.setCantidad(e.getMonto().doubleValue());
        mov.setMoneda(e.getMoneda());
        mov.setUsuario(usuario);
        mov.setDescripcion(e.getDescripcion());
        mov.setReferenciaId(saved.getId());
        mov.setOrigenTipo(OrigenMovimientoTipo.ENTRADA_VARIA);
        mov.setOrigenId(saved.getId());
        MovimientoCajaVirtual posteado = tesoreriaService.registrar(mov);

        saved.setMovimientoCajaVirtualId(posteado.getId());
        return repository.save(saved);
    }

    /** Anula la entrada/salida y revierte su movimiento de caja. */
    @Transactional
    public EntradaVaria anular(Long id, String motivo, Usuario usuario) {
        EntradaVaria e = repository.findById(id)
                .orElseThrow(() -> new GraphQLException("Entrada varia no encontrada: " + id));
        if (Boolean.TRUE.equals(e.getAnulado())) {
            throw new GraphQLException("La entrada/salida ya está anulada");
        }
        // Reversión desde el módulo dueño (EntradaVaria), vía el helper sin guard cross-módulo.
        if (e.getMovimientoCajaVirtualId() != null) {
            MovimientoCajaVirtual orig = tesoreriaService.findMovimiento(e.getMovimientoCajaVirtualId());
            tesoreriaService.revertir(orig, motivo != null ? motivo : "anulación de entrada varia", usuario);
        }
        e.setAnulado(true);
        return repository.save(e);
    }
}
