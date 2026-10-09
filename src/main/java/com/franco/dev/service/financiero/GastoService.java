package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Service
@AllArgsConstructor
public class GastoService extends CrudService<Gasto, GastoRepository, EmbebedPrimaryKey> {

    private final GastoRepository repository;
    private final org.springframework.context.ApplicationEventPublisher publisher;

    public static final DecimalFormat df = new DecimalFormat("#,###.##");

    @Override
    public GastoRepository getRepository() {
        return repository;
    }

    public List<Gasto> findByDate(String inicio, String fin, Long sucId) {
        return repository.findBySucursalIdAndCreadoEnBetween(sucId, stringToDate(inicio), stringToDate(fin));
    }

    public List<Gasto> filterGastos(Long id, Long cajaId, Long sucId, Long responsableId, String descripcion,
            Pageable pageable) {
        return repository.findByAll(id, cajaId, sucId, responsableId, descripcion, pageable);
    }

    public Page<Gasto> filterGastosPage(Long id, Long cajaId, Long sucId, Long responsableId, String descripcion,
            Pageable pageable) {
        return repository.findByAllPage(id, cajaId, sucId, responsableId, descripcion, pageable);
    }

    public List<Gasto> findByCajaId(Long id, Long sucId) {
        return repository.findByCajaIdAndSucursalId(id, sucId);
    }

    public Gasto findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    @Override
    public Gasto save(Gasto entity) {
        Gasto e = super.save(entity);
        publisher.publishEvent(new com.franco.dev.fmc.event.GastoRealizadoEvent(this, e));
        return e;
    }

    /**
     * Cancela ({@code cancelar = true}) o habilita ({@code false}) un gasto, igual que
     * {@link RetiroService#cancelarRetiro}: el pedido dice cómo tiene que quedar y repetirlo no cambia
     * nada (issue #376). Sin el argumento —un desktop anterior— significa cancelar y nunca habilita.
     *
     * <p>No recalcula ningún balance: PdvCajaService.generarBalance ignora los gastos cancelados, y la
     * filial hace lo mismo cuando el flag le llega por replicación. No pasa por {@link #save}: su
     * override publica GastoRealizadoEvent, y cancelar no es realizar un gasto.</p>
     *
     * <p>Un gasto pagado desde la caja mayor es el espejo de su solicitud de pago: la fuente de verdad
     * es la solicitud, y GastoTesoreriaService lo mantiene sincronizado. Tocarlo por separado dejaría
     * el gasto cancelado con el pago intacto (y la próxima sincronización lo revertiría), así que se
     * corta acá con un mensaje que dice dónde se hace de verdad.</p>
     */
    @Transactional
    public Boolean cancelarGasto(Long id, Long sucId, Boolean cancelar) {
        repository.lockByIdAndSucursalId(id, sucId)
                .orElseThrow(() -> new GraphQLException("Gasto no encontrado: " + id + "/" + sucId));
        List<Object[]> filas = repository.findCanceladoYSolicitud(id, sucId);
        if (filas.isEmpty()) throw new GraphQLException("Gasto no encontrado: " + id + "/" + sucId);
        boolean cancelado = Boolean.TRUE.equals(filas.get(0)[0]);
        Object solicitudPagoId = filas.get(0)[1];

        if (solicitudPagoId != null) {
            throw new GraphQLException("Este gasto se pagó desde la caja mayor: para "
                    + (Boolean.FALSE.equals(cancelar) ? "habilitarlo" : "cancelarlo")
                    + " hay que anular el pago del gasto #" + solicitudPagoId + " en la caja mayor.");
        }
        if (Boolean.FALSE.equals(cancelar)) {
            if (cancelado) repository.marcarCancelado(id, sucId, false);
            return true;
        }
        if (cancelado) {
            if (cancelar == null) {
                throw new GraphQLException("El gasto #" + id + " ya está cancelado. Para habilitarlo actualizá el sistema.");
            }
            return true;
        }
        repository.marcarCancelado(id, sucId, true);
        return true;
    }

    public List<com.franco.dev.domain.financiero.GastoPorCategoria> gastosPorCategoria(String inicio, String fin,
            Long sucId) {
        java.time.LocalDateTime fechaInicio = stringToDate(inicio);
        java.time.LocalDateTime fechaFin = stringToDate(fin);
        // sucId 0 es la sucursal SERVIDOR (gastos pagados desde la caja mayor), no el
        // centinela de "todas": null es el unico que significa todas.
        Long sucursalIdFiltro = (sucId != null && sucId >= 0) ? sucId : null;
        List<Object[]> results = sucursalIdFiltro != null
                ? repository.gastosPorCategoria(fechaInicio, fechaFin, sucursalIdFiltro)
                : repository.gastosPorCategoriaSinSucursal(fechaInicio, fechaFin);
        java.util.List<com.franco.dev.domain.financiero.GastoPorCategoria> list = new java.util.ArrayList<>();
        for (Object[] obj : results) {
            com.franco.dev.domain.financiero.GastoPorCategoria dto = new com.franco.dev.domain.financiero.GastoPorCategoria();
            dto.setCategoria(obj[0] != null ? String.valueOf(obj[0]) : "");
            dto.setTotal(obj[1] != null ? ((Number) obj[1]).doubleValue() : 0.0);
            dto.setCantidad(obj[2] != null ? ((Number) obj[2]).longValue() : 0L);
            list.add(dto);
        }
        return list;
    }

    public List<com.franco.dev.domain.financiero.GastoPorMes> gastosPorMes(Integer anio, Long sucId) {
        java.time.LocalDateTime inicio = java.time.LocalDateTime.of(anio, 1, 1, 0, 0);
        java.time.LocalDateTime fin = java.time.LocalDateTime.of(anio, 12, 31, 23, 59, 59);
        // Idem gastosPorCategoria: 0 es SERVIDOR, no "todas".
        Long sucursalIdFiltro = (sucId != null && sucId >= 0) ? sucId : null;
        List<Object[]> results = sucursalIdFiltro != null
                ? repository.gastosPorMes(inicio, fin, sucursalIdFiltro)
                : repository.gastosPorMesSinSucursal(inicio, fin);
        java.util.List<com.franco.dev.domain.financiero.GastoPorMes> list = new java.util.ArrayList<>();
        for (Object[] obj : results) {
            com.franco.dev.domain.financiero.GastoPorMes dto = new com.franco.dev.domain.financiero.GastoPorMes();
            dto.setMes(obj[0] != null ? ((Number) obj[0]).intValue() : null);
            dto.setTotal(obj[1] != null ? ((Number) obj[1]).doubleValue() : 0.0);
            dto.setCantidad(obj[2] != null ? ((Number) obj[2]).longValue() : 0L);
            list.add(dto);
        }
        return list;
    }
}