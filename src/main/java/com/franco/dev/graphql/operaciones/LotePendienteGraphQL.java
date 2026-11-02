package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.dto.InfoPendienteLoteDto;
import com.franco.dev.domain.operaciones.dto.PendienteLotePageDto;
import com.franco.dev.domain.operaciones.dto.ResolucionLotePendienteDto;
import com.franco.dev.service.operaciones.InventarioSecurityService;
import com.franco.dev.service.operaciones.LotePendienteService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Recepciones pendientes de lote (#429, fase 2, entrega 3).
 *
 * Ver la lista y el contador: VER PRODUCTOS o EDITAR PRODUCTOS. Completar y descartar: EDITAR
 * PRODUCTOS (igual que ajustar stock por lote). Reabrir: ADMIN. El autor de cada resolucion es el
 * usuario de la sesion, nunca un id que mande el cliente.
 */
@Component
public class LotePendienteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    static final String VER_PRODUCTOS = "VER PRODUCTOS";
    static final String EDITAR_PRODUCTOS = "EDITAR PRODUCTOS";

    @Autowired private LotePendienteService service;
    @Autowired private InventarioSecurityService seguridad;

    public PendienteLotePageDto recepcionesConLotePendiente(Long sucursalId, String desde, String hasta,
                                                           Integer page, Integer size) {
        exigir(VER_PRODUCTOS, EDITAR_PRODUCTOS);
        return run(() -> service.listar(sucursalId, desde, hasta, page, size));
    }

    public Long contarRecepcionesConLotePendiente(Long sucursalId) {
        exigir(VER_PRODUCTOS, EDITAR_PRODUCTOS);
        return service.contar(sucursalId);
    }

    public InfoPendienteLoteDto infoLotePendiente(Long mslId, Long sucursalId, String numeroLote) {
        exigir(EDITAR_PRODUCTOS);
        return run(() -> service.info(mslId, sucursalId, numeroLote));
    }

    public List<ResolucionLotePendienteDto> resolucionesLotePendiente(Long sucursalId, Integer page, Integer size) {
        exigir(VER_PRODUCTOS, EDITAR_PRODUCTOS);
        return service.resoluciones(sucursalId, page, size);
    }

    public ResolucionLotePendienteDto completarLotePendiente(Long mslId, Long sucursalId, String numeroLote,
                                                            String fechaVencimiento, Double cantidad,
                                                            Double saldoSinLoteEsperado, Boolean cerrarRemanente,
                                                            String motivoRemanente, Boolean confirmarVencimiento) {
        exigir(EDITAR_PRODUCTOS);
        Long usuarioId = seguridad.requireUsuarioDeSesion().getId();
        return run(() -> service.completar(mslId, sucursalId, numeroLote, fechaVencimiento, cantidad,
                saldoSinLoteEsperado, cerrarRemanente, motivoRemanente, confirmarVencimiento, usuarioId));
    }

    public ResolucionLotePendienteDto descartarLotePendiente(Long mslId, Long sucursalId, String motivo) {
        exigir(EDITAR_PRODUCTOS);
        Long usuarioId = seguridad.requireUsuarioDeSesion().getId();
        return run(() -> service.descartar(mslId, sucursalId, motivo, usuarioId));
    }

    public ResolucionLotePendienteDto reabrirLotePendiente(Long resolucionId, String motivo) {
        exigir(InventarioSecurityService.ADMIN);
        Long usuarioId = seguridad.requireUsuarioDeSesion().getId();
        return run(() -> service.reabrir(resolucionId, motivo, usuarioId));
    }

    private void exigir(String... roles) {
        if (!seguridad.hasAnyRole(roles)) {
            throw new GraphQLException("No autorizado: se requiere " + String.join(" o ", roles)
                    + " para los pendientes de lote.");
        }
    }

    private static <T> T run(java.util.function.Supplier<T> s) {
        try {
            return s.get();
        } catch (IllegalArgumentException e) {
            throw new GraphQLException(e.getMessage());
        }
    }
}
