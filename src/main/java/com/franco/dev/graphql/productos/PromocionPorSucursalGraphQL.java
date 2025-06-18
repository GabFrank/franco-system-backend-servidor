package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.productos.PromocionPorSucursal;
import com.franco.dev.graphql.productos.input.PromocionPorSucursalInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.productos.PromocionPorSucursalService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PromocionPorSucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PromocionPorSucursalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private Environment env;

    /**
     * Queries
     */
    public Optional<PromocionPorSucursal> promocionPorSucursal(Long id) {
        return service.findById(id);
    }

    public List<PromocionPorSucursal> promocionesPorSucursal(Long sucursalId) {
        return service.findBySucursalId(sucursalId);
    }

    public List<PromocionPorSucursal> promocionesPorPresentacion(Long presentacionId) {
        return service.findByPresentacionId(presentacionId);
    }

    public List<PromocionPorSucursal> promocionesPorPrecioId(Long precioId) {
        return service.findByPrecioId(precioId);
    }

    public List<PromocionPorSucursal> promocionesActivasPorSucursal(Long sucursalId) {
        return service.findActiveBySucursalId(sucursalId);
    }

    public List<PromocionPorSucursal> promocionesActivasPorPresentacion(Long presentacionId) {
        return service.findActiveByPresentacionId(presentacionId);
    }

    public List<PromocionPorSucursal> promocionesPorTipo(String tipoPromocion) {
        return service.findByTipoPromocion(tipoPromocion);
    }

    public List<PromocionPorSucursal> promocionesPorSucursal(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public VerificacionPromocionResult verificarPromociones(Long presentacionId, List<Long> sucursalesIds) {
        List<PromocionPorSucursal> promocionesEncontradas = service.findByPresentacionId(presentacionId);
        List<Long> sucursalesConPromocion = promocionesEncontradas.stream()
                .map(p -> p.getSucursal().getId())
                .distinct()
                .collect(Collectors.toList());

        List<Long> faltantes = new ArrayList<>(sucursalesIds);
        faltantes.removeAll(sucursalesConPromocion);

        List<Long> extras = new ArrayList<>(sucursalesConPromocion);
        extras.removeAll(sucursalesIds);

        boolean todasOk = faltantes.isEmpty() && extras.isEmpty();

        return new VerificacionPromocionResult(todasOk, promocionesEncontradas, faltantes, extras, sucursalesConPromocion);
    }

    /**
     * Mutations
     */
    public PromocionPorSucursal savePromocionPorSucursal(PromocionPorSucursalInput input) {
        ModelMapper m = new ModelMapper();
        PromocionPorSucursal entity = m.map(input, PromocionPorSucursal.class);
        
        // Mapear relaciones
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getPrecioId() != null) {
            entity.setPrecio(precioPorSucursalService.findById(input.getPrecioId()).orElse(null));
        }
        if (input.getSucursalId() != null) {
            entity.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        }

        return service.save(entity);
    }

    public BulkPromocionResult saveBulkPromocionPorSucursal(List<PromocionPorSucursalInput> promociones) {
        System.out.println("=== DEBUG BACKEND SAVEBULK ===");
        System.out.println("promociones recibidas: " + promociones);
        System.out.println("promociones es null: " + (promociones == null));
        if (promociones != null) {
            System.out.println("promociones size: " + promociones.size());
            for (int i = 0; i < promociones.size(); i++) {
                PromocionPorSucursalInput input = promociones.get(i);
                System.out.println("Input " + i + ":");
                System.out.println("  precioId: " + input.getPrecioId() + " (tipo: " + (input.getPrecioId() != null ? input.getPrecioId().getClass().getSimpleName() : "null") + ")");
                System.out.println("  sucursalId: " + input.getSucursalId() + " (tipo: " + (input.getSucursalId() != null ? input.getSucursalId().getClass().getSimpleName() : "null") + ")");
                System.out.println("  usuarioId: " + input.getUsuarioId() + " (tipo: " + (input.getUsuarioId() != null ? input.getUsuarioId().getClass().getSimpleName() : "null") + ")");
                System.out.println("  activo: " + input.getActivo());
                System.out.println("  esPromocion: " + input.getEsPromocion());
                System.out.println("  tipoPromocion: " + input.getTipoPromocion());
            }
        }
        
        ModelMapper m = new ModelMapper();
        List<PromocionPorSucursal> entities = promociones.stream().map(input -> {
            PromocionPorSucursal entity = m.map(input, PromocionPorSucursal.class);
            
            // Mapear relaciones
            if (input.getUsuarioId() != null) {
                entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
            }
            if (input.getPrecioId() != null) {
                entity.setPrecio(precioPorSucursalService.findById(input.getPrecioId()).orElse(null));
            }
            if (input.getSucursalId() != null) {
                entity.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
            }
            
            return entity;
        }).collect(Collectors.toList());

        List<PromocionPorSucursal> result = service.saveBulk(entities);
        
        return new BulkPromocionResult(true, result.size());
    }

    public Boolean deletePromocionPorSucursal(Long id) {
        return service.deleteById(id);
    }

    public Boolean deleteBulkPromocionPorSucursal(List<Long> ids) {
        try {
            service.deleteBulk(ids);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Long countPromocionPorSucursal() {
        return service.count();
    }

    /**
     * Clase auxiliar para respuesta de operaciones en lote
     */
    public static class BulkPromocionResult {
        private Boolean success;
        private Integer count;

        public BulkPromocionResult(Boolean success, Integer count) {
            this.success = success;
            this.count = count;
        }

        public Boolean getSuccess() { return success; }
        public Integer getCount() { return count; }
    }

    /**
     * Clase auxiliar para respuesta de verificación de promociones
     */
    public static class VerificacionPromocionResult {
        private final Boolean todasLasSucursalesOk;
        private final List<PromocionPorSucursal> promocionesEncontradas;
        private final List<Long> faltantes;
        private final List<Long> extras;
        private final List<Long> sucursalesConPromocion;

        public VerificacionPromocionResult(Boolean todasLasSucursalesOk, List<PromocionPorSucursal> promocionesEncontradas, List<Long> faltantes, List<Long> extras, List<Long> sucursalesConPromocion) {
            this.todasLasSucursalesOk = todasLasSucursalesOk;
            this.promocionesEncontradas = promocionesEncontradas;
            this.faltantes = faltantes;
            this.extras = extras;
            this.sucursalesConPromocion = sucursalesConPromocion;
        }

        public Boolean getTodasLasSucursalesOk() { return todasLasSucursalesOk; }
        public List<PromocionPorSucursal> getPromocionesEncontradas() { return promocionesEncontradas; }
        public List<Long> getFaltantes() { return faltantes; }
        public List<Long> getExtras() { return extras; }
        public List<Long> getSucursalesConPromocion() { return sucursalesConPromocion; }
    }

} 