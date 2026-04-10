package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Vehiculo;
import com.franco.dev.repository.activos.VehiculoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import org.springframework.context.annotation.Lazy;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VehiculoService extends CrudService<Vehiculo, VehiculoRepository, Long> {

    private final VehiculoRepository repository;
    private final EnteService enteService;

    public VehiculoService(VehiculoRepository repository, @Lazy EnteService enteService) {
        this.repository = repository;
        this.enteService = enteService;
    }

    @Override
    public VehiculoRepository getRepository() {
        return repository;
    }

    public Vehiculo findByChapa(String chapa) {
        return repository.findByChapa(chapa);
    }

    public List<Vehiculo> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public Page<Vehiculo> findByAllWithPage(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable);
    }

    public List<Vehiculo> findByMarcaId(Long marcaId) {
        return repository.findByMarcaId(marcaId);
    }

    public List<Vehiculo> findByModeloId(Long modeloId) {
        return repository.findByModeloId(modeloId);
    }

    public List<Vehiculo> findByTipoVehiculoId(Long tipoVehiculoId) {
        return repository.findByTipoVehiculoId(tipoVehiculoId);
    }

    public List<Vehiculo> findByPropietarioId(Long propietarioId) {
        return repository.findByPropietarioId(propietarioId);
    }

    public List<Vehiculo> findByTipoCombustibleId(Long tipoCombustibleId) {
        return repository.findByTipoCombustibleId(tipoCombustibleId);
    }

    @Override
    public Vehiculo save(Vehiculo entity) {
        if (entity.getId() == null)
            entity.setCreadoEn(LocalDateTime.now());

        // Validación: chapa única
        if (entity.getChapa() != null && !entity.getChapa().isEmpty()) {
            Vehiculo existente = repository.findByChapa(entity.getChapa());
            if (existente != null && !existente.getId().equals(entity.getId())) {
                throw new RuntimeException("Ya existe un vehículo con la chapa: " + entity.getChapa());
            }
            entity.setChapa(entity.getChapa().toUpperCase());
        }

        // Validación: año entre 1900 y año actual+1
        if (entity.getAnho() != null) {
            int anhoActual = LocalDateTime.now().getYear();
            if (entity.getAnho() < 1900 || entity.getAnho() > (anhoActual + 1)) {
                throw new RuntimeException("El año debe estar entre 1900 y " + (anhoActual + 1));
            }
        }

        // Validación: capacidades >= 0
        if (entity.getCapacidadKg() != null && entity.getCapacidadKg().doubleValue() < 0) {
            throw new RuntimeException("La capacidad en kg debe ser mayor o igual a 0");
        }
        if (entity.getCapacidadPasajeros() != null && entity.getCapacidadPasajeros() < 0) {
            throw new RuntimeException("La capacidad de pasajeros debe ser mayor o igual a 0");
        }

        normalizeFinancialData(entity);
        Vehiculo e = super.save(entity);
        if (e != null) {
            String descripcion = e.getChapa() != null ? "Chapa: " + e.getChapa()
                    : (e.getModelo() != null ? e.getModelo().getDescripcion() : "Vehículo #" + e.getId());
            enteService.ensureEnteForReferencia(TipoEnte.VEHICULO, e.getId(), descripcion, e.getUsuario());
        }
        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        enteService.findByTipoEnteAndReferenciaId(TipoEnte.VEHICULO, id).ifPresent(ente -> {
            enteService.deleteById(ente.getId());
        });
        return super.deleteById(id);
    }

    private void normalizeFinancialData(Vehiculo entity) {
        if (entity.getSituacionPago() != null) {
            String situacion = entity.getSituacionPago().toUpperCase();
            if (situacion.equals("PAGADO") || situacion.equals("DADO") || 
                situacion.equals("GANADO") || situacion.equals("COMODATO")) {
                
                if (entity.getMontoTotal() != null) {
                    entity.setMontoYaPagado(entity.getMontoTotal());
                }
                if (entity.getCantidadCuotas() != null) {
                    entity.setCantidadCuotasPagadas(entity.getCantidadCuotas());
                } else {
                    entity.setCantidadCuotas(0);
                    entity.setCantidadCuotasPagadas(0);
                }
            } else if (situacion.equals("PAGANDO")) {
                java.math.BigDecimal total = entity.getMontoTotal() != null ? entity.getMontoTotal() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal pagado = entity.getMontoYaPagado() != null ? entity.getMontoYaPagado() : java.math.BigDecimal.ZERO;
                Integer cuotasTotal = entity.getCantidadCuotas() != null ? entity.getCantidadCuotas() : 0;
                Integer cuotasPagadas = entity.getCantidadCuotasPagadas() != null ? entity.getCantidadCuotasPagadas() : 0;

                if (total.compareTo(java.math.BigDecimal.ZERO) > 0 && pagado.compareTo(total) >= 0 && cuotasPagadas >= cuotasTotal) {
                    entity.setSituacionPago("PAGADO");
                }
            }
        }
    }
}
