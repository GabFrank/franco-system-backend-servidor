package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaNota;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaNotaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class RecepcionMercaderiaNotaService extends CrudService<RecepcionMercaderiaNota, RecepcionMercaderiaNotaRepository, Long> {
    
    private final RecepcionMercaderiaNotaRepository repository;
    
    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Override
    public RecepcionMercaderiaNotaRepository getRepository() {
        return repository;
    }

    /**
     * Busca asociaciones por ID de recepción de mercadería
     */
    public List<RecepcionMercaderiaNota> findByRecepcionMercaderiaId(Long recepcionId) {
        return repository.findByRecepcionMercaderiaId(recepcionId);
    }

    /**
     * Busca asociaciones por ID de nota de recepción
     */
    public List<RecepcionMercaderiaNota> findByNotaRecepcionId(Long notaId) {
        return repository.findByNotaRecepcionId(notaId);
    }

    /**
     * Asocia una nota de recepción a una recepción de mercadería
     */
    @Transactional
    public RecepcionMercaderiaNota asociarNotaARecepcion(RecepcionMercaderia recepcion, Long notaRecepcionId) {
        // Verificar que la nota existe
        NotaRecepcion notaRecepcion = notaRecepcionService.findById(notaRecepcionId)
            .orElseThrow(() -> new IllegalArgumentException("Nota de recepción no encontrada: " + notaRecepcionId));
        
        // Verificar que no esté ya asociada
        List<RecepcionMercaderiaNota> existentes = repository.findByRecepcionAndNota(
            recepcion.getId(), notaRecepcionId);
        
        if (!existentes.isEmpty()) {
            throw new IllegalStateException("La nota ya está asociada a esta recepción");
        }
        
        // Crear la asociación
        RecepcionMercaderiaNota asociacion = new RecepcionMercaderiaNota();
        asociacion.setRecepcionMercaderia(recepcion);
        asociacion.setNotaRecepcion(notaRecepcion);
        
        return save(asociacion);
    }

    /**
     * Desasocia una nota de recepción de una recepción de mercadería
     */
    @Transactional
    public boolean desasociarNotaDeRecepcion(Long recepcionId, Long notaRecepcionId) {
        List<RecepcionMercaderiaNota> asociaciones = repository.findByRecepcionAndNota(
            recepcionId, notaRecepcionId);
        
        if (asociaciones.isEmpty()) {
            return false;
        }
        
        for (RecepcionMercaderiaNota asociacion : asociaciones) {
            delete(asociacion);
        }
        
        return true;
    }
} 