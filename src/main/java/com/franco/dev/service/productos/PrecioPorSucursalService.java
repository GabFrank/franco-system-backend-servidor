package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.repository.productos.PrecioPorSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PrecioPorSucursalService extends CrudService<PrecioPorSucursal, PrecioPorSucursalRepository, Long> {

    @Autowired
    private final PrecioPorSucursalRepository repository;
    @Autowired
    private final com.franco.dev.service.configuraciones.ModificacionService modificacionService;

    @Override
    public PrecioPorSucursalRepository getRepository() {
        return repository;
    }

    public List<PrecioPorSucursal> findByPresentacionId(Long id) {
        return repository.findByPresentacionId(id);
    }

    public List<PrecioPorSucursal> findBySucursalId(Long id) {
        return repository.findBySucursalId(id);
    }

    @Override
    public PrecioPorSucursal save(PrecioPorSucursal entity) {
        if (entity.getId() == null)
            entity.setCreadoEn(LocalDateTime.now());

        PrecioPorSucursal entidadAnterior = null;
        boolean esNuevo = (entity.getId() == null);
        if (!esNuevo) {
            java.util.Optional<PrecioPorSucursal> precioOpt = repository.findById(entity.getId());
            if (precioOpt != null && precioOpt.isPresent()) {
                PrecioPorSucursal original = precioOpt.get();
                entidadAnterior = new PrecioPorSucursal();
                entidadAnterior.setId(original.getId());
                entidadAnterior.setPrecio(original.getPrecio());
                entidadAnterior.setPrincipal(original.getPrincipal());
                entidadAnterior.setActivo(original.getActivo());
                entidadAnterior.setCreadoEn(original.getCreadoEn());
                entidadAnterior.setPresentacion(original.getPresentacion());
                entidadAnterior.setTipoPrecio(original.getTipoPrecio());
                entidadAnterior.setSucursal(original.getSucursal());
                entidadAnterior.setUsuario(original.getUsuario());
            }
        }

        PrecioPorSucursal p = super.save(entity);
        repository.flush();

        try {
            if (esNuevo) {
                modificacionService.registrarInsercion(p, "PRECIO_POR_SUCURSAL", "productos", "precio_por_sucursal");
            } else if (entidadAnterior != null) {
                modificacionService.registrarActualizacion(entidadAnterior, p, "PRECIO_POR_SUCURSAL", "productos",
                        "precio_por_sucursal");
            }
        } catch (Exception ex) {
            System.err.println("Error registrando modificación de precio por sucursal: " + ex.getMessage());
            ex.printStackTrace();
        }

        return p;
    }

    @Override
    @javax.transaction.Transactional
    public Boolean deleteById(Long id) {
        try {
            PrecioPorSucursal entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                Boolean resultado = super.deleteById(id);
                try {
                    modificacionService.registrarEliminacion(entidad, "PRECIO_POR_SUCURSAL", "productos",
                            "precio_por_sucursal");
                } catch (Exception ex) {
                    System.err.println("Error registrando eliminación de precio por sucursal: " + ex.getMessage());
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }

    public PrecioPorSucursal findPrincipalByPrecionacionId(Long id) {
        return repository.findPrincipalByPresentacionId(id);
    }

    public PrecioPorSucursal findBySucursalIdAndPresentacionId(Long sucId, Long preId) {
        return repository.findBySucursalIdAndPresentacionId(sucId, preId);
    }
}
