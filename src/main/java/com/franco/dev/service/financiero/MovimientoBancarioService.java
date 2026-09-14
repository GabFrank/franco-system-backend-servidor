package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consultas de movimientos bancarios con filtros. La escritura del ledger vive en
 * {@link BancoLedgerService}; esto es solo lectura para la lista y el reporte del dashboard.
 */
@Service
@AllArgsConstructor
public class MovimientoBancarioService {

    private final MovimientoBancarioRepository repository;

    public Page<MovimientoBancario> filter(Long cuentaBancariaId, String desde, String fin, String tipo,
                                           boolean soloActivos, Pageable pageable) {
        return repository.filter(cuentaBancariaId,
                MovimientoCajaVirtualService.inicioRango(desde), MovimientoCajaVirtualService.finRango(fin),
                tipoValido(tipo), soloActivos, pageable);
    }

    public List<MovimientoBancario> filterList(Long cuentaBancariaId, String desde, String fin, String tipo,
                                               boolean soloActivos) {
        return repository.filterList(cuentaBancariaId,
                MovimientoCajaVirtualService.inicioRango(desde), MovimientoCajaVirtualService.finRango(fin),
                tipoValido(tipo), soloActivos);
    }

    /** El tipo llega como String del schema: se valida contra el enum para no filtrar por basura. */
    static String tipoValido(String tipo) {
        if (tipo == null || tipo.trim().isEmpty()) return null;
        try {
            return MovimientoBancarioTipo.valueOf(tipo.trim()).name();
        } catch (IllegalArgumentException e) {
            throw new GraphQLException("Tipo de movimiento bancario desconocido: " + tipo);
        }
    }
}
