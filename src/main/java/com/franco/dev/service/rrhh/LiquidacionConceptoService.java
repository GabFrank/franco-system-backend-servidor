package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionConcepto;
import com.franco.dev.repository.rrhh.LiquidacionConceptoRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@AllArgsConstructor
public class LiquidacionConceptoService extends CrudService<LiquidacionConcepto, LiquidacionConceptoRepository, Long> {

    private final LiquidacionConceptoRepository repository;

    @Override
    public LiquidacionConceptoRepository getRepository() {
        return repository;
    }

    /**
     * Orden del catálogo: por número de operación (en Postgres, ASC deja los null al final), después el orden
     * de siempre (haberes primero, por descripción) con el código como desempate determinista.
     */
    static final Sort ORDEN = Sort.by(Sort.Order.asc("numero"), Sort.Order.desc("esHaber"),
            Sort.Order.asc("descripcion"), Sort.Order.asc("codigo"));

    /** Los que se ofrecen en el select de item manual. Ver el repositorio. */
    public java.util.List<LiquidacionConcepto> findParaItemManual() {
        return repository.findByActivoTrueAndEsCalculadoAutoFalse(ORDEN);
    }

    /** La lista del ABM, en el mismo orden. */
    public java.util.List<LiquidacionConcepto> findAllOrdenado(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, ORDEN)).getContent();
    }

    public Optional<LiquidacionConcepto> findByCodigo(String codigo) {
        if (codigo == null) return Optional.empty();
        return repository.findByCodigo(codigo.toUpperCase());
    }

    @Override
    public LiquidacionConcepto save(LiquidacionConcepto entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getActivo() == null) entity.setActivo(true);
        if (entity.getEsHaber() == null) entity.setEsHaber(true);
        if (entity.getEsCalculadoAuto() == null) entity.setEsCalculadoAuto(false);
        // Default true, igual que la columna: un cliente viejo que no manda el campo no
        // puede reventar el insert. Es la red, no el camino normal: el ABM del desktop
        // (list-liquidacion-concepto) pide el valor explicitamente en el alta, para que
        // nadie entre a la base remunerativa del aguinaldo y del IPS por omision.
        if (entity.getEsRemunerativo() == null) entity.setEsRemunerativo(true);
        if (entity.getCodigo() != null) entity.setCodigo(entity.getCodigo().toUpperCase());
        validarNumero(entity);
        try {
            // saveAndFlush: la violación del índice único (dos ediciones a la vez) sale acá, no al commit.
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (entity.getNumero() != null) {
                throw new GraphQLException("El numero " + entity.getNumero() + " ya lo usa otra operacion activa");
            }
            throw e;
        }
    }

    /**
     * Número > 0 y único entre activos. Corre al crear, al cambiar el número y al reactivar (un concepto
     * inactivo puede tener un número que mientras tanto tomó otro).
     */
    private void validarNumero(LiquidacionConcepto entity) {
        Integer numero = entity.getNumero();
        if (numero == null) return;
        if (numero <= 0) throw new GraphQLException("El numero de operacion debe ser mayor a cero");
        if (!Boolean.TRUE.equals(entity.getActivo())) return;
        for (LiquidacionConcepto otro : repository.findByNumeroAndActivoTrue(numero)) {
            if (!otro.getId().equals(entity.getId())) {
                throw new GraphQLException("El numero " + numero + " ya lo usa " + otro.getDescripcion());
            }
        }
    }
}
