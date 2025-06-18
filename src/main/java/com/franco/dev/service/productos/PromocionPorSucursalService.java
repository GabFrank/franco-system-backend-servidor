package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.PromocionPorSucursal;
import com.franco.dev.repository.productos.PromocionPorSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PromocionPorSucursalService extends CrudService<PromocionPorSucursal, PromocionPorSucursalRepository, Long> {

    @Autowired
    private final PromocionPorSucursalRepository repository;

    @Override
    public PromocionPorSucursalRepository getRepository() {
        return repository;
    }

    @Override
    public PromocionPorSucursal save(PromocionPorSucursal entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }

    /**
     * Guarda múltiples promociones en lote
     */
    public List<PromocionPorSucursal> saveBulk(List<PromocionPorSucursal> promociones) {
        for (PromocionPorSucursal promocion : promociones) {
            if (promocion.getId() == null) {
                promocion.setCreadoEn(LocalDateTime.now());
            }
        }
        return repository.saveAll(promociones);
    }

    /**
     * Elimina múltiples promociones en lote
     */
    public void deleteBulk(List<Long> ids) {
        for (Long id : ids) {
            repository.deleteById(id);
        }
    }

    /**
     * Encuentra promociones por sucursal
     */
    public List<PromocionPorSucursal> findBySucursalId(Long sucursalId) {
        return repository.findBySucursalId(sucursalId);
    }

    /**
     * Encuentra promociones activas por sucursal
     */
    public List<PromocionPorSucursal> findActiveBySucursalId(Long sucursalId) {
        return repository.findBySucursalIdAndActivoTrue(sucursalId);
    }

    /**
     * Encuentra promociones por presentación
     */
    public List<PromocionPorSucursal> findByPresentacionId(Long presentacionId) {
        return repository.findByPresentacionId(presentacionId);
    }

    /**
     * Encuentra promociones activas por presentación
     */
    public List<PromocionPorSucursal> findActiveByPresentacionId(Long presentacionId) {
        return repository.findActiveByPresentacionId(presentacionId);
    }

    /**
     * Encuentra promociones por precio
     */
    public List<PromocionPorSucursal> findByPrecioId(Long precioId) {
        return repository.findByPrecioId(precioId);
    }

    /**
     * Encuentra promociones por tipo
     */
    public List<PromocionPorSucursal> findByTipoPromocion(String tipoPromocion) {
        return repository.findByTipoPromocion(tipoPromocion);
    }

    /**
     * Activa/desactiva promociones por precio en sucursales específicas
     */
    public List<PromocionPorSucursal> togglePromocionPorSucursales(Long precioId, List<Long> sucursalesIds, Boolean activo, String tipoPromocion) {
        List<PromocionPorSucursal> promocionesExistentes = repository.findByPrecioId(precioId);
        
        for (Long sucursalId : sucursalesIds) {
            PromocionPorSucursal promocion = promocionesExistentes.stream()
                    .filter(p -> p.getSucursal().getId().equals(sucursalId))
                    .findFirst()
                    .orElse(null);

            if (promocion == null) {
                // Crear nueva promoción
                promocion = new PromocionPorSucursal();
                promocion.getPrecio().setId(precioId);
                promocion.getSucursal().setId(sucursalId);
                promocion.setCreadoEn(LocalDateTime.now());
            }

            promocion.setActivo(activo);
            promocion.setEsPromocion(activo);
            promocion.setTipoPromocion(activo ? tipoPromocion : null);
            
            repository.save(promocion);
        }

        return repository.findByPrecioId(precioId);
    }

} 