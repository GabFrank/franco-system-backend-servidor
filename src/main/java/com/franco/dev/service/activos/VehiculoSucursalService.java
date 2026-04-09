package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.VehiculoSucursal;
import com.franco.dev.repository.activos.VehiculoSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.EnteSucursal;
import com.franco.dev.domain.activos.enums.TipoEnte;
import org.springframework.context.annotation.Lazy;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VehiculoSucursalService extends CrudService<VehiculoSucursal, VehiculoSucursalRepository, Long> {

    private final VehiculoSucursalRepository repository;
    private final EnteService enteService;
    private final EnteSucursalService enteSucursalService;

    public VehiculoSucursalService(VehiculoSucursalRepository repository, @Lazy EnteService enteService, @Lazy EnteSucursalService enteSucursalService) {
        this.repository = repository;
        this.enteService = enteService;
        this.enteSucursalService = enteSucursalService;
    }

    @Override
    public VehiculoSucursalRepository getRepository() {
        return repository;
    }

    public List<VehiculoSucursal> findByVehiculoId(Long vehiculoId) {
        return repository.findByVehiculoId(vehiculoId);
    }

    public List<VehiculoSucursal> findBySucursalId(Long sucursalId) {
        return repository.findBySucursalId(sucursalId);
    }

    public List<VehiculoSucursal> findByVehiculoIdAndSucursalId(Long vehiculoId, Long sucursalId) {
        return repository.findByVehiculoIdAndSucursalId(vehiculoId, sucursalId);
    }

    public Page<VehiculoSucursal> findBySucursalAndResponsable(Long sucursalId, Long responsableId, Pageable pageable) {
        return repository.findAllBySucursalAndResponsable(sucursalId, responsableId, pageable);
    }

    @Override
    public VehiculoSucursal save(VehiculoSucursal entity) {
        if (entity.getId() == null)
            entity.setCreadoEn(LocalDateTime.now());
        VehiculoSucursal e = super.save(entity);
        if (e != null && e.getVehiculo() != null && e.getSucursal() != null) {
            Ente ente = enteService.ensureEnteForReferencia(TipoEnte.VEHICULO, e.getVehiculo().getId(), e.getUsuario());
            if (ente != null) {
                EnteSucursal enteSucursal = enteSucursalService.findByEnteId(ente.getId()).stream()
                        .filter(asig -> asig.getSucursal().getId().equals(e.getSucursal().getId()))
                        .findFirst().orElseGet(EnteSucursal::new);
                enteSucursal.setEnte(ente);
                enteSucursal.setSucursal(e.getSucursal());
                enteSucursal.setResponsable(e.getResponsable());
                enteSucursal.setUsuario(e.getUsuario());
                enteSucursalService.save(enteSucursal);
            }
        }
        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        VehiculoSucursal e = repository.findById(id).orElse(null);
        if (e != null && e.getVehiculo() != null && e.getSucursal() != null) {
            enteService.findByTipoEnteAndReferenciaId(TipoEnte.VEHICULO, e.getVehiculo().getId()).ifPresent(ente -> {
                enteSucursalService.findByEnteId(ente.getId()).stream()
                        .filter(asig -> asig.getSucursal().getId().equals(e.getSucursal().getId()))
                        .findFirst().ifPresent(asig -> enteSucursalService.deleteById(asig.getId()));
            });
        }
        return super.deleteById(id);
    }
}
