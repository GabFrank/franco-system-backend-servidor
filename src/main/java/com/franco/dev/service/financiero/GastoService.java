package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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

    public List<com.franco.dev.domain.financiero.GastoPorCategoria> gastosPorCategoria(String inicio, String fin,
            Long sucId) {
        java.time.LocalDateTime fechaInicio = stringToDate(inicio);
        java.time.LocalDateTime fechaFin = stringToDate(fin);
        return repository.gastosPorCategoria(fechaInicio, fechaFin, sucId);
    }

    public List<com.franco.dev.domain.financiero.GastoPorMes> gastosPorMes(Integer anio, Long sucId) {
        java.time.LocalDateTime inicio = java.time.LocalDateTime.of(anio, 1, 1, 0, 0);
        java.time.LocalDateTime fin = java.time.LocalDateTime.of(anio, 12, 31, 23, 59, 59);
        return repository.gastosPorMes(inicio, fin, sucId);
    }
}