package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Service
@AllArgsConstructor
public class GastoService extends CrudService<Gasto, GastoRepository, EmbebedPrimaryKey> {

    private final GastoRepository repository;

    @Override
    public GastoRepository getRepository() {
        return repository;
    }

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private CambioService cambioService;

//    public List<Gasto> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<Gasto> findByDate(String inicio, String fin, Long sucId){
        return repository.findBySucursalIdAndCreadoEnBetween(sucId, toDate(inicio), toDate(fin));
    }

    public List<Gasto> filterGastos(Long id, Long cajaId, Long sucId, Long responsableId, Pageable pageable){
        return repository.findByAll(id, cajaId, sucId, responsableId, pageable);
    }

    public List<Gasto> findByCajaId(Long id, Long sucId) {
        return repository.findByCajaIdAndSucursalId(id, sucId);
    }

    @Override
    public Gasto save(Gasto entity) {
        Gasto e = super.save(entity);
//        List<Moneda> monedaList = monedaService.findAll2();
//        MovimientoCaja movimientoCaja = new MovimientoCaja();
//        movimientoCaja.setTipoMovimiento(PdvCajaTipoMovimiento.GASTO);
        // guarani

//        if(e.getFinalizado()==true){
//            if(e.getRetiroGs()>0){
//                movimientoCaja.setCantidad(e.getRetiroGs() * -1);
//                movimientoCaja.setUsuario(e.getUsuario());
//                movimientoCaja.setMoneda(monedaService.findByDescripcion("GUARANI"));
//                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
//                movimientoCaja.setPdvCaja(e.getCaja());
//                movimientoCaja.setReferencia(e.getId());
//                movimientoCaja.setCreadoEn(e.getCreadoEn());
//                movimientoCajaService.save(movimientoCaja);
//            }
//
//            //real
//            if(e.getRetiroRs()>0){
//                movimientoCaja.setCantidad(e.getRetiroRs() * -1);
//                movimientoCaja.setUsuario(e.getUsuario());
//                movimientoCaja.setMoneda(monedaService.findByDescripcion("REAL"));
//                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
//                movimientoCaja.setPdvCaja(e.getCaja());
//                movimientoCaja.setReferencia(e.getId());
//                movimientoCaja.setCreadoEn(e.getCreadoEn());
//                movimientoCajaService.save(movimientoCaja);
//            }
//            // dolar
//            if(e.getRetiroDs()>0){
//                movimientoCaja.setCantidad(e.getRetiroDs() * -1);
//                movimientoCaja.setUsuario(e.getUsuario());
//                movimientoCaja.setMoneda(monedaService.findByDescripcion("DOLAR"));
//                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
//                movimientoCaja.setPdvCaja(e.getCaja());
//                movimientoCaja.setReferencia(e.getId());
//                movimientoCaja.setCreadoEn(e.getCreadoEn());
//                movimientoCajaService.save(movimientoCaja);
//            }
//            if(e.getVueltoGs()>0){
//                movimientoCaja.setCantidad(e.getVueltoGs());
//                movimientoCaja.setUsuario(e.getUsuario());
//                movimientoCaja.setMoneda(monedaService.findByDescripcion("GUARANI"));
//                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
//                movimientoCaja.setPdvCaja(e.getCaja());
//                movimientoCaja.setReferencia(e.getId());
//                movimientoCaja.setCreadoEn(e.getCreadoEn());
//                movimientoCajaService.save(movimientoCaja);
//            }
//
//            //real
//            if(e.getVueltoRs()>0){
//                movimientoCaja.setCantidad(e.getVueltoRs());
//                movimientoCaja.setUsuario(e.getUsuario());
//                movimientoCaja.setMoneda(monedaService.findByDescripcion("REAL"));
//                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
//                movimientoCaja.setPdvCaja(e.getCaja());
//                movimientoCaja.setReferencia(e.getId());
//                movimientoCaja.setCreadoEn(e.getCreadoEn());
//                movimientoCajaService.save(movimientoCaja);
//            }
//            // dolar
//            if(e.getVueltoDs()>0){
//                movimientoCaja.setCantidad(e.getVueltoDs());
//                movimientoCaja.setUsuario(e.getUsuario());
//                movimientoCaja.setMoneda(monedaService.findByDescripcion("DOLAR"));
//                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
//                movimientoCaja.setPdvCaja(e.getCaja());
//                movimientoCaja.setReferencia(e.getId());
//                movimientoCaja.setCreadoEn(e.getCreadoEn());
//                movimientoCajaService.save(movimientoCaja);
//            }
//        }

//        personaPublisher.publish(p);
        return e;
    }
}