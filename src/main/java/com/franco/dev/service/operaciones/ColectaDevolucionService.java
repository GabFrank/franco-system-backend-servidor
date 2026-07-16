package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.ColectaDevolucion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.operaciones.ColectaDevolucionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Cabecera de la operacion de colecta interna (origen -> destino). CRUD y
 * consultas; la orquestacion vive en DevolucionService.colectarEnBloque.
 */
@Service
public class ColectaDevolucionService {

    @Autowired
    private ColectaDevolucionRepository repository;

    @Transactional
    public ColectaDevolucion crear(Sucursal origen, Sucursal destino, Usuario usuario) {
        ColectaDevolucion c = new ColectaDevolucion();
        c.setFecha(LocalDateTime.now());
        c.setCreadoEn(LocalDateTime.now());
        c.setUsuario(usuario);
        c.setSucursalOrigen(origen);
        c.setSucursalDestino(destino);
        c.setEstado(ColectaDevolucion.ESTADO_CONFIRMADO);
        return repository.save(c);
    }

    public Optional<ColectaDevolucion> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public ColectaDevolucion save(ColectaDevolucion c) {
        return repository.save(c);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public Page<ColectaDevolucion> findByFilters(Long sucursalOrigenId, Long sucursalDestinoId,
                                                 LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                                 Pageable pageable) {
        return repository.findByFilters(sucursalOrigenId, sucursalDestinoId, fechaInicio, fechaFin, pageable);
    }
}
