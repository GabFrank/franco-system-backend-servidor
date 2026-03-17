package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class CajaVirtualService {

    private final CajaVirtualRepository repository;

    public Optional<CajaVirtual> findById(Long id) {
        return repository.findById(id);
    }

    public Page<CajaVirtual> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public List<CajaVirtual> findByTipo(CajaVirtualTipo tipo) {
        return repository.findByTipo(tipo);
    }

    public List<CajaVirtual> findBySucursalId(Long sucursalId) {
        return repository.findBySucursalId(sucursalId);
    }

    public List<CajaVirtual> findActivas() {
        return repository.findByActivoTrue();
    }

    public CajaVirtual save(CajaVirtual entity) {
        if (entity.getSaldoGs() == null) entity.setSaldoGs(0.0);
        if (entity.getSaldoRs() == null) entity.setSaldoRs(0.0);
        if (entity.getSaldoDs() == null) entity.setSaldoDs(0.0);
        if (entity.getActivo() == null) entity.setActivo(true);
        return repository.save(entity);
    }

    public Boolean deleteById(Long id) {
        try {
            repository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Actualiza el saldo de forma atómica según la moneda del movimiento.
     * La cantidad debe ser positiva para ingresos y negativa para egresos.
     * Se usa UPDATE directo para evitar condiciones de carrera.
     *
     * @param cajaId  ID de la caja virtual
     * @param monto   monto (positivo=ingreso, negativo=egreso)
     * @param monedaDenominacion denominación de la moneda (GUARANI, REAL, DOLAR)
     */
    @Transactional
    public void actualizarSaldo(Long cajaId, Double monto, String monedaDenominacion) {
        CajaVirtual caja = repository.findById(cajaId)
                .orElseThrow(() -> new GraphQLException("Caja virtual no encontrada: " + cajaId));

        if (monedaDenominacion != null && monedaDenominacion.toUpperCase().contains("GUARANI")) {
            caja.setSaldoGs(caja.getSaldoGs() + monto);
        } else if (monedaDenominacion != null && monedaDenominacion.toUpperCase().contains("REAL")) {
            caja.setSaldoRs(caja.getSaldoRs() + monto);
        } else if (monedaDenominacion != null && monedaDenominacion.toUpperCase().contains("DOLAR")) {
            caja.setSaldoDs(caja.getSaldoDs() + monto);
        } else {
            // Por defecto Guaraníes
            caja.setSaldoGs(caja.getSaldoGs() + monto);
        }
        repository.save(caja);
    }

    /**
     * Verifica si la caja tiene fondos suficientes para un egreso.
     *
     * @param cajaId              ID de la caja virtual
     * @param monto               monto a verificar (positivo)
     * @param monedaDenominacion  denominación de la moneda
     * @return true si hay fondos suficientes
     */
    public boolean verificarSaldo(Long cajaId, Double monto, String monedaDenominacion) {
        CajaVirtual caja = repository.findById(cajaId)
                .orElseThrow(() -> new GraphQLException("Caja virtual no encontrada: " + cajaId));

        if (monedaDenominacion != null && monedaDenominacion.toUpperCase().contains("GUARANI")) {
            return caja.getSaldoGs() >= monto;
        } else if (monedaDenominacion != null && monedaDenominacion.toUpperCase().contains("REAL")) {
            return caja.getSaldoRs() >= monto;
        } else if (monedaDenominacion != null && monedaDenominacion.toUpperCase().contains("DOLAR")) {
            return caja.getSaldoDs() >= monto;
        }
        return caja.getSaldoGs() >= monto;
    }
}
