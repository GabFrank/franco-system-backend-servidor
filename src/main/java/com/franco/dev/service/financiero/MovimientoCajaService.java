package com.franco.dev.service.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.MovimientoCajaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MovimientoCajaService extends CrudService<MovimientoCaja, MovimientoCajaRepository, EmbebedPrimaryKey> {

    private final MovimientoCajaRepository repository;

    @Autowired
    private MultiTenantService multiTenantService;

    @Override
    public MovimientoCajaRepository getRepository() {
        return repository;
    }

//    public List<MovimientoCaja> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

//    public List<MovimientoCaja> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return repository.findByAll(texto);
//    }

    public List<MovimientoCaja> findByPdvCajaId(Long id, Long sucId) {
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> repository.findByPdvCajaIdAndActivoAndSucursalId((Long)params[0], (Boolean) params[1], (Long) params[2]), id, true,  sucId);
//        return repository.findByPdvCajaIdAndActivoAndSucursalId(id, true, sucId);
    }

    public Double findTotalVentaByCajaIdAndMonedaId(Long id, Long monedaId, Long sucId) {
        Double total = 0.0;
//        total = repository.findTotalVentaByCajaIdAndMonedaId(id, monedaId, sucId);
        total = multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> repository.findTotalVentaByCajaIdAndMonedaId((Long)params[0], (Long) params[1], (Long) params[2]), id, monedaId, sucId);
        if (total == null) {
            return 0.0;
        } else {
            return total;
        }
    }

    public List<MovimientoCaja> findByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento tipoMovimiento, Long referencia, Long sucId) {
//        return repository.findByTipoMovimientoAndReferenciaAndSucursalId(tipoMovimiento, referencia, sucId);
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> repository.findByTipoMovimientoAndReferenciaAndSucursalId((PdvCajaTipoMovimiento) params[0], (Long) params[1], (Long) params[2]), tipoMovimiento, referencia, sucId);

    }

    public Double totalEnCajaPorCajaIdAndMonedaId(Long cajaId, Long monedaId, Long sucId) {
//        Double total = repository.totalEnCajaByCajaIdandMonedaId(cajaId, monedaId, sucId);
        Double total = multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> repository.totalEnCajaByCajaIdandMonedaId((Long) params[0], (Long) params[1], (Long) params[2]), cajaId, monedaId, sucId);
        return total != null ? total : 0.0;
    }

    @Override
    public MovimientoCaja save(MovimientoCaja entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        MovimientoCaja e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

//    @Override
//    public MovimientoCaja saveAndSend(MovimientoCaja entity, Boolean recibir) {
//        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
//        MovimientoCaja e = super.save(entity);
//        propagacionService.propagarEntidad(e, TipoEntidad.MOVIMIENTO_CAJA, e.getSucursalId());
//        return e;
//    }

    @Override
    public MovimientoCaja saveAndSend(MovimientoCaja entity, Long sucId) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        MovimientoCaja e = super.save(entity);
        multiTenantService.compartir("filial"+sucId+"_bkp", (MovimientoCaja s) -> super.save(s), e);
        return e;
    }
}