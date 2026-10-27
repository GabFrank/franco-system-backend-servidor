package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionMonedaModulo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import com.franco.dev.repository.financiero.ConfiguracionMonedaModuloRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Que moneda usa un modulo en un instante. Mismo contrato que {@code ConfiguracionFacturacionLector}
 * del filial: nunca lanza, no cachea (la tabla es chica y un cambio rige en el acto) y corre fuera
 * de la transaccion de quien llama, para que una falla al leer no la deje rollback-only.
 * <p>
 * Orden: la fila de la sucursal (solo PDV) -> la fila de toda la empresa -> la moneda de la empresa.
 * De cada clave cuenta la fila activa mas reciente con {@code vigente_desde <= instante}: desactivar
 * la ultima devuelve la clave a la anterior, y sin ninguna, al nivel siguiente.
 */
@Service
@AllArgsConstructor
public class MonedaModuloLector {

    private static final Logger log = LoggerFactory.getLogger(MonedaModuloLector.class);

    private final ConfiguracionMonedaModuloRepository repository;
    private final MonedaService monedaService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public Moneda resolver(ModuloMoneda modulo, Long sucursalId, LocalDateTime instante) {
        try {
            LocalDateTime cuando = instante != null ? instante : LocalDateTime.now();
            List<ConfiguracionMonedaModulo> filas = repository.findByModuloOrderByVigenteDesdeDescIdDesc(modulo);
            if (modulo == ModuloMoneda.PDV && sucursalId != null) {
                Optional<Moneda> deSucursal = vigente(filas, sucursalId, cuando);
                if (deSucursal.isPresent()) return deSucursal.get();
            }
            Optional<Moneda> global = vigente(filas, null, cuando);
            if (global.isPresent()) return global.get();
        } catch (Exception e) {
            log.warn("MONEDA_MODULO_ILEGIBLE modulo={} sucursal={}: se usa la moneda de la empresa", modulo, sucursalId, e);
        }
        return empresa();
    }

    /** La moneda de un modulo ahora. */
    public Moneda resolver(ModuloMoneda modulo, Long sucursalId) {
        return resolver(modulo, sucursalId, null);
    }

    /** La moneda de la empresa, sin lanzar: null solo si la base no responde. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public Moneda empresa() {
        try {
            return monedaService.empresa();
        } catch (Exception e) {
            log.warn("MONEDA_EMPRESA_ILEGIBLE", e);
            return null;
        }
    }

    /** Filas ordenadas de la mas reciente a la mas vieja: la primera activa y vigente de la clave. */
    static Optional<Moneda> vigente(List<ConfiguracionMonedaModulo> filas, Long sucursalId, LocalDateTime cuando) {
        for (ConfiguracionMonedaModulo f : filas) {
            Long sucursalFila = f.getSucursal() != null ? f.getSucursal().getId() : null;
            if (!Objects.equals(sucursalFila, sucursalId)) continue;
            if (f.getVigenteDesde() != null && f.getVigenteDesde().isAfter(cuando)) continue;
            if (Boolean.FALSE.equals(f.getActivo())) continue;
            if (f.getMoneda() == null) continue;
            return Optional.of(f.getMoneda());
        }
        return Optional.empty();
    }
}
