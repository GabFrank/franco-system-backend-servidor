package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.RetiroDevolucion;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.operaciones.RetiroDevolucionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Cabecera de la operacion de retiro a proveedor. CRUD y consultas; la
 * orquestacion (crear cabecera + avanzar estados + vincular) vive en
 * DevolucionService.retirarEnBloque para reutilizar la maquina de estados.
 */
@Service
public class RetiroDevolucionService {

    @Autowired
    private RetiroDevolucionRepository repository;

    @Transactional
    public RetiroDevolucion crear(Proveedor proveedor, Usuario usuario) {
        RetiroDevolucion r = new RetiroDevolucion();
        r.setFecha(LocalDateTime.now());
        r.setCreadoEn(LocalDateTime.now());
        r.setUsuario(usuario);
        r.setProveedor(proveedor);
        r.setEstado(RetiroDevolucion.ESTADO_CONFIRMADO);
        return repository.save(r);
    }

    public Optional<RetiroDevolucion> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public RetiroDevolucion save(RetiroDevolucion r) {
        return repository.save(r);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public Page<RetiroDevolucion> findByFilters(Long proveedorId, LocalDateTime fechaInicio,
                                                LocalDateTime fechaFin, Pageable pageable) {
        return repository.findByFilters(proveedorId, fechaInicio, fechaFin, pageable);
    }
}
