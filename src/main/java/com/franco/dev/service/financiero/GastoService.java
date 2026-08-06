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
     * Cancela o rehabilita un gasto, igual que RetiroService.cancelarRetiro: es un
     * toggle cancelado <-> no cancelado.
     *
     * No recalcula ningun balance. El monto vuelve a la caja porque
     * PdvCajaService.generarBalance ignora los gastos cancelados, y la filial hace
     * lo mismo cuando el flag le llega por replicacion.
     *
     * Persiste con repository.save() a proposito, y no con this.save(): el override
     * de save() publica GastoRealizadoEvent, que dispara la push notification de
     * gasto realizado. Cancelar no es realizar un gasto.
     */
    @Transactional
    public Boolean cancelarGasto(Gasto gasto) {
        try {
            gasto.setCancelado(!Boolean.TRUE.equals(gasto.getCancelado()));
            repository.save(gasto);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("No se pudo cancelar el gasto");
        }
    }

    public List<com.franco.dev.domain.financiero.GastoPorCategoria> gastosPorCategoria(String inicio, String fin,
            Long sucId) {
        java.time.LocalDateTime fechaInicio = stringToDate(inicio);
        java.time.LocalDateTime fechaFin = stringToDate(fin);
        Long sucursalIdFiltro = (sucId != null && sucId > 0) ? sucId : null;
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
        Long sucursalIdFiltro = (sucId != null && sucId > 0) ? sucId : null;
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