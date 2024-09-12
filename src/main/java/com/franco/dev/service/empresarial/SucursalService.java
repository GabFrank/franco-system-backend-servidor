package com.franco.dev.service.empresarial;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.repository.empresarial.SucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<Sucursal> findByAll(String texto) {
        texto = texto != null ? texto.replace(" ", "%").toUpperCase() : "";
        return repository.findByAll(texto);

    }

    @Override
    public List<Sucursal> findAll(Pageable pageable) {
        return repository.findAllByOrderByIdAsc();
    }

    public List<Sucursal> findAllNotConfigured() {
        return repository.findByIsConfiguredFalse();
    }

    @Override
    public Sucursal save(Sucursal entity) {
        Sucursal e = super.save(entity);
        return e;
    }

    public Sucursal sucursalActual() {
        return findById(Long.valueOf(environment.getProperty("sucursalId"))).orElse(null);
    }
}