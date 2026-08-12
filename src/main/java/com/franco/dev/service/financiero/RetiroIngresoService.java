package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.repository.financiero.RetiroRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingreso <b>manual on-demand</b> de un retiro flotante a una caja mayor.
 *
 * <p>Un retiro replicado del PDV llega con {@code cajaVirtualId = null} ("flotando"). El
 * usuario de tesorería elige a qué caja mayor va: este servicio asigna la caja y dispara el
 * mismo motor de posteo que usa el poller ({@link RetiroTesoreriaProcesador#procesar}), sin
 * esperar los 60s del scheduler (que además está apagado por default).</p>
 *
 * <p>El posteo real (agregación por moneda, INGRESO tagueado {@code RETIRO_CAJA}, marcado de
 * idempotencia) vive en {@code RetiroTesoreriaProcesador} — acá solo se asigna la caja y se
 * delega, para no duplicar la lógica ni el guard anti doble-ingreso.</p>
 */
@Service
@AllArgsConstructor
public class RetiroIngresoService {

    private final RetiroRepository retiroRepository;
    private final RetiroTesoreriaProcesador procesador;
    private final CajaVirtualService cajaVirtualService;

    @Transactional
    public Retiro ingresarACajaMayor(Long retiroId, Long sucursalId, Long cajaVirtualId,
                                     com.franco.dev.domain.personas.Usuario usuario) {
        if (cajaVirtualId == null) {
            throw new GraphQLException("Debe indicar la caja mayor destino");
        }
        Retiro r = retiroRepository.findByIdAndSucursalId(retiroId, sucursalId);
        if (r == null) {
            throw new GraphQLException("Retiro no encontrado: " + retiroId + "/" + sucursalId);
        }
        if (r.getMovimientoCajaVirtualId() != null) {
            throw new GraphQLException("El retiro #" + retiroId + " ya fue ingresado a una caja mayor");
        }
        cajaVirtualService.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja mayor no encontrada: " + cajaVirtualId));

        r.setCajaVirtualId(cajaVirtualId);
        retiroRepository.save(r);

        // Delegación al motor de posteo (mismo que el poller). Es un bean separado a propósito
        // para que su @Transactional aplique vía proxy y comparta esta transacción.
        procesador.procesar(retiroId, sucursalId, usuario);

        return retiroRepository.findByIdAndSucursalId(retiroId, sucursalId);
    }
}
