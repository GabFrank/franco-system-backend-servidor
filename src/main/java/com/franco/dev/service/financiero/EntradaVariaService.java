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
    private final ComprobanteNumeracionService comprobanteNumeracionService;

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
        // Antes del save: el tipeado no puede repetirse, y vacío es «sin número» (el desktop manda ''), que
        // se autonumera si hay serie (issue #376).
        e.setNumeroComprobante(comprobanteNumeracionService.resolver("ENTRADA_VARIA", e.getNumeroComprobante(),
                repository::existeComprobante, "una entrada varia"));
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
        // Con lock: sin él, dos anulaciones simultáneas leían las dos la entrada sin anular (issue #376). El
        // estado se relee después del lock y de la base: lockById devuelve la instancia que ya estuviera
        // cargada en la request, con el anulado de antes de esperar.
        EntradaVaria e = repository.lockById(id)
                .orElseThrow(() -> new GraphQLException("Entrada varia no encontrada: " + id));
        if (repository.findAnuladoById(id).orElse(Boolean.TRUE.equals(e.getAnulado()))) {
            throw new GraphQLException("La entrada/salida ya está anulada");
        }
        // Límite de antigüedad sobre la fecha de la entrada (issue #370).
        tesoreriaService.requireDentroDelLimiteDeAnulacion(e.getCreadoEn(), "La entrada/salida #" + id);
        // Reversión desde el módulo dueño (EntradaVaria), vía el helper sin guard cross-módulo.
        if (e.getMovimientoCajaVirtualId() != null) {
            MovimientoCajaVirtual orig = tesoreriaService.findMovimiento(e.getMovimientoCajaVirtualId());
            tesoreriaService.revertir(orig, motivo != null ? motivo : "anulación de entrada varia", usuario);
        }
        e.setAnulado(true);
        return repository.save(e);
    }
}
