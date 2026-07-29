package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.TransferenciaItemLote;
import com.franco.dev.domain.operaciones.dto.TransferenciaItemLoteDto;
import com.franco.dev.domain.operaciones.enums.EtapaAsignacionLote;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.repository.operaciones.TransferenciaItemLoteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Asignacion manual de lotes a un item de transferencia.
 *
 * Guarda lo que el operador eligio. El desglose real del stock lo sigue haciendo
 * {@link MovimientoStockLoteService}: este servicio solo responde "que lotes pidio el operador
 * para este item", y si no pidio ninguno el flujo cae a FEFO como siempre.
 */
@Service
@AllArgsConstructor
public class TransferenciaItemLoteService
        extends CrudService<TransferenciaItemLote, TransferenciaItemLoteRepository, Long> {

    private final TransferenciaItemLoteRepository repository;

    private final LoteService loteService;

    @Override
    public TransferenciaItemLoteRepository getRepository() {
        return repository;
    }

    /**
     * Reemplaza la asignacion de una etapa por la que se recibe.
     *
     * Se borra y se vuelve a crear en vez de intentar un diff porque el operador puede reordenar,
     * quitar o cambiar cantidades entre lotes en una misma edicion, y el diff no aporta nada:
     * son pocas filas y no hay nada que preservar de las anteriores.
     *
     * @param asignaciones lista con el reparto elegido. Null o vacia borra la asignacion de esa
     *                     etapa, con lo que el item vuelve a resolverse por FEFO.
     */
    @Transactional
    public List<TransferenciaItemLote> reemplazarAsignacion(TransferenciaItem item,
                                                            EtapaAsignacionLote etapa,
                                                            List<AsignacionSolicitada> asignaciones,
                                                            Usuario usuario) {
        List<TransferenciaItemLote> creados = new ArrayList<>();
        if (item == null || item.getId() == null || etapa == null) {
            return creados;
        }

        repository.deleteByTransferenciaItemIdAndEtapa(item.getId(), etapa);
        // Flush explicito: Hibernate encola los INSERT antes que los DELETE dentro del mismo
        // flush, y si el operador vuelve a elegir un lote que ya tenia asignado, el insert
        // chocaria contra uq_til_item_etapa_lote antes de que se ejecute el borrado.
        repository.flush();

        if (asignaciones == null || asignaciones.isEmpty()) {
            return creados;
        }

        for (AsignacionSolicitada solicitada : asignaciones) {
            if (solicitada == null || solicitada.getLoteId() == null) {
                continue;
            }
            Double cantidad = solicitada.getCantidad();
            if (cantidad == null || cantidad <= 0) {
                continue;
            }
            Lote lote = loteService.findById(solicitada.getLoteId()).orElse(null);
            if (lote == null) {
                log.warning("Asignacion manual con lote inexistente " + solicitada.getLoteId()
                        + " en transferencia item " + item.getId() + ": se ignora esa fila.");
                continue;
            }

            TransferenciaItemLote fila = new TransferenciaItemLote();
            fila.setTransferenciaItem(item);
            fila.setLote(lote);
            fila.setNumeroLote(lote.getNumeroLote());
            fila.setCantidad(cantidad);
            fila.setEtapa(etapa);
            fila.setUsuario(usuario);
            fila.setCreadoEn(LocalDateTime.now());
            creados.add(save(fila));
        }
        return creados;
    }

    public List<TransferenciaItemLote> asignacionPorEtapa(Long transferenciaItemId, EtapaAsignacionLote etapa) {
        if (transferenciaItemId == null || etapa == null) {
            return new ArrayList<>();
        }
        return repository.findByTransferenciaItemIdAndEtapaOrderByIdAsc(transferenciaItemId, etapa);
    }

    public List<TransferenciaItemLote> asignacionesPorItem(Long transferenciaItemId) {
        if (transferenciaItemId == null) {
            return new ArrayList<>();
        }
        return repository.findByTransferenciaItemIdOrderByIdAsc(transferenciaItemId);
    }

    /**
     * Asignaciones de un item con los datos del maestro ya resueltos y las cantidades expresadas
     * tambien en la presentacion del item, para que la pantalla no tenga que convertir nada.
     */
    public List<TransferenciaItemLoteDto> asignacionesDtoPorItem(TransferenciaItem item) {
        if (item == null || item.getId() == null) {
            return new ArrayList<>();
        }
        return asignacionesPorItem(item.getId()).stream()
                .map(fila -> aDto(fila, item))
                .collect(Collectors.toList());
    }

    private TransferenciaItemLoteDto aDto(TransferenciaItemLote fila, TransferenciaItem item) {
        Lote lote = fila.getLote();
        // La presentacion depende de la etapa en la que se eligieron los lotes: en preparacion el
        // item puede haberse preparado con otra distinta a la de creacion.
        Presentacion presentacion = fila.getEtapa() == EtapaAsignacionLote.PREPARACION
                && item.getPresentacionPreparacion() != null
                ? item.getPresentacionPreparacion()
                : item.getPresentacionPreTransferencia();
        double unidadesPorPresentacion = ConversionPresentacion.unidadesPorPresentacion(presentacion);

        return new TransferenciaItemLoteDto(
                fila.getId(),
                lote != null ? lote.getId() : null,
                fila.getNumeroLote(),
                fila.getCantidad(),
                ConversionPresentacion.aPresentaciones(fila.getCantidad(), unidadesPorPresentacion),
                fila.getEtapa(),
                lote != null ? lote.getFechaVencimiento() : null,
                lote != null ? lote.getFechaRetiro() : null,
                lote != null ? lote.getEstado() : null,
                fila.getUsuario(),
                fila.getCreadoEn()
        );
    }

    /**
     * Asignacion que manda para resolver el desglose de un item.
     *
     * PREPARACION gana sobre PRE_TRANSFERENCIA: lo que se preparo fisicamente reemplaza a lo que
     * se habia pedido al crear el item. Si no hay ninguna de las dos, devuelve vacio y el llamador
     * resuelve por FEFO.
     */
    public List<TransferenciaItemLote> asignacionVigente(Long transferenciaItemId) {
        List<TransferenciaItemLote> preparacion =
                asignacionPorEtapa(transferenciaItemId, EtapaAsignacionLote.PREPARACION);
        if (!preparacion.isEmpty()) {
            return preparacion;
        }
        return asignacionPorEtapa(transferenciaItemId, EtapaAsignacionLote.PRE_TRANSFERENCIA);
    }

    /**
     * Lo que el llamador pide guardar. Se usa un tipo propio y no el input de GraphQL para que el
     * servicio no dependa de la capa de API.
     */
    public static class AsignacionSolicitada {
        private final Long loteId;
        private final Double cantidad;

        public AsignacionSolicitada(Long loteId, Double cantidad) {
            this.loteId = loteId;
            this.cantidad = cantidad;
        }

        public Long getLoteId() {
            return loteId;
        }

        public Double getCantidad() {
            return cantidad;
        }
    }
}
