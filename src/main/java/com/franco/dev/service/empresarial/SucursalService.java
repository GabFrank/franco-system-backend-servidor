package com.franco.dev.service.empresarial;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.repository.empresarial.SucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SucursalService extends CrudService<Sucursal, SucursalRepository, Long> {

    private final SucursalRepository repository;
    @Autowired
    private Environment environment;
    private MultiTenantService multiTenantService;

    @Autowired
    public SucursalService(@Lazy MultiTenantService multiTenantService, SucursalRepository repository) {
        this.multiTenantService = multiTenantService;
        this.repository = repository;
    }

    @Override
    public SucursalRepository getRepository() {
        return repository;
    }

    public List<Sucursal> findByAll(String texto, Boolean activo) {
        texto = texto != null ? texto.replace(" ", "%").toUpperCase() : "";
        return repository.findByAllWithActivoFilter(texto, activo != null ? activo : true);
    }

    public Page<Sucursal> findByAllPage(String texto, Boolean activo, Pageable pageable) {
        texto = texto != null ? texto.replace(" ", "%").toUpperCase() : "";
        return repository.findByAllWithActivoFilterPage(texto, activo != null ? activo : true, pageable);
    }

    public List<Sucursal> findByNombreConFiltros(String nombre, Boolean deposito, Boolean activo, Pageable pageable) {
        nombre = nombre != null ? nombre.replace(" ", "%").toUpperCase() : "";
        return repository.findByNombreConFiltros(nombre, deposito, activo, pageable).getContent();
    }

    @Override
    public List<Sucursal> findAll(Pageable pageable) {
        return repository.findAllByActivoTrueOrderByIdAsc();
    }

    public List<Sucursal> findAllNotConfigured() {
        return repository.findByIsConfiguredFalse();
    }

    @Override
    public Sucursal save(Sucursal entity) {
        if(entity.getId() == null){
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }

    public Sucursal sucursalActual() {
        String sId = environment.getProperty("sucursalId");
        Long id = 0L;
        if (sId != null) {
            try {
                id = Long.valueOf(sId);
            } catch (NumberFormatException e) {
                id = 0L;
            }
        }
        return findById(id).orElse(null);
    }

    /**
     * Obtiene todas las sucursales activas excluyendo el servidor (id 0)
     * 
     * Usado en:
     * - Desktop: Sí (módulo de compras, opción "Todos" en sucursales)
     * - Mobile: No
     */
    public List<Sucursal> findAllExcludingServer() {
        return repository.findAllByActivoTrueOrderByIdAsc()
                .stream()
                .filter(s -> !s.getId().equals(0L))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las sucursales de entrega: deposito=true y activo=true
     * 
     * Usado en:
     * - Desktop: Sí (módulo de compras, opción "Todos" en sucursales de entrega)
     * - Mobile: No
     */
    public List<Sucursal> findAllSucursalesEntrega() {
        return repository.findAllByActivoTrueOrderByIdAsc()
                .stream()
                .filter(s -> Boolean.TRUE.equals(s.getDeposito()) && Boolean.TRUE.equals(s.getActivo()))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las sucursales de influencia: activo=true y excluir servidor (id 0)
     * 
     * Usado en:
     * - Desktop: Sí (módulo de compras, opción "Todos" en sucursales de influencia)
     * - Mobile: No
     */
    public List<Sucursal> findAllSucursalesInfluencia() {
        return repository.findAllByActivoTrueOrderByIdAsc()
                .stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()) && !s.getId().equals(0L))
                .collect(Collectors.toList());
    }
}