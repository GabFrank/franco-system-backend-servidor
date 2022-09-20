package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.dto.VentaPorPeriodoV1Dto;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
@AllArgsConstructor
public class VentaService extends CrudService<Venta, VentaRepository> {
    private final VentaRepository repository;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private CobroDetalleService cobroDetalleService;

    @Autowired
    private PropagacionService propagacionService;

    @Override
    public VentaRepository getRepository() {
        return repository;
    }



//    public List<Venta> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//    }

    public List<Venta> findByCajaId(Long id, Integer offset) {
        return repository.findByCajaId(id, offset);
    }

    public List<Venta> findAllByCajaId(Long id) {
        return repository.findByCajaId(id);
    }

    @Override
    public Venta save(Venta entity) {
        Venta e = super.save(entity);
        return e;
    }

    @Override
    public Venta saveAndSend(Venta entity, Boolean recibir) {
        Venta e = super.save(entity);
        propagacionService.propagarEntidad(e, TipoEntidad.VENTA, entity.getSucursalId());
        return e;
    }

    public List<VentaPorPeriodoV1Dto> ventaPorPeriodo(String inicio, String fin){
        List<VentaPorPeriodoV1Dto> ventaPorPeriodoList = new ArrayList<>();
        LocalDateTime fechaInicio = LocalDateTime.parse(inicio);
        LocalDateTime fechaFin = LocalDateTime.parse(fin);
        Long cantDias = DAYS.between(fechaInicio, fechaFin);
        for (int i = 0; i < cantDias; i++) {
            VentaPorPeriodoV1Dto ventaPorPeriodoV1Dto = new VentaPorPeriodoV1Dto();
            ventaPorPeriodoV1Dto.setCreadoEn(fechaInicio.plusDays(i));
            ventaPorPeriodoList.add(ventaPorPeriodoV1Dto);
        }
        for (VentaPorPeriodoV1Dto ventaPorPeriodo : ventaPorPeriodoList) {
            List<Venta> ventaList = repository.ventaPorPeriodo(ventaPorPeriodo.getCreadoEn(), ventaPorPeriodo.getCreadoEn().plusDays(1));
            ventaPorPeriodo.setCantVenta(ventaList.size());
            for (Venta venta : ventaList) {
                if (venta.getEstado() != VentaEstado.CANCELADA || venta.getEstado() != VentaEstado.ABIERTA) {
                    List<CobroDetalle> cobroDetalleList = cobroDetalleService.findByCobroId(venta.getCobro().getId());
                    for (CobroDetalle cobroDetalle : cobroDetalleList) {
                        if (cobroDetalle.getMoneda().getDenominacion().contains("GUARANI")) {
                            if (cobroDetalle.getPago()) {
                                ventaPorPeriodo.addGs(cobroDetalle.getValor());
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor());
                            } else if (cobroDetalle.getDescuento()) {
                                ventaPorPeriodo.addGs(cobroDetalle.getValor() * -1);
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * -1);
                            }
                        }
                        if (cobroDetalle.getMoneda().getDenominacion().contains("REAL")) {
                            if (cobroDetalle.getPago()) {
                                ventaPorPeriodo.addRs(cobroDetalle.getValor());
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * cobroDetalle.getCambio());
                            } else if (cobroDetalle.getDescuento()) {
                                ventaPorPeriodo.addRs(cobroDetalle.getValor() * -1);
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * -1 * cobroDetalle.getCambio());
                            }
                        }
                        if (cobroDetalle.getMoneda().getDenominacion().contains("DOLAR")) {
                            if (cobroDetalle.getPago()) {
                                ventaPorPeriodo.addDs(cobroDetalle.getValor());
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * cobroDetalle.getCambio());
                            } else if (cobroDetalle.getDescuento()) {
                                ventaPorPeriodo.addDs(cobroDetalle.getValor() * -1);
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * -1 * cobroDetalle.getCambio());
                            }
                        }
                    }
                }
            }
        }
        return ventaPorPeriodoList;
    }

    @Transactional
    public Boolean cancelarVenta(Venta venta){
        venta.setEstado(VentaEstado.CANCELADA);
        saveAndSend(venta, false);
        List<MovimientoCaja> movimientoCajaList = movimientoCajaService.findByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento.VENTA, venta.getCobro().getId());
        for(MovimientoCaja mov : movimientoCajaList){
            mov.setActivo(false);
            movimientoCajaService.saveAndSend(mov, false);
        }
        return true;
    }
}