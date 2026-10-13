package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualSucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.empresarial.SucursalRepository;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.CajaVirtualSucursalRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sucursales adicionales que cubre una caja virtual: de ellas, además de la propia, recibe retiros del PDV
 * quien la opera con el rol acotado.
 */
@Service
@AllArgsConstructor
public class CajaVirtualSucursalService {

    private final CajaVirtualSucursalRepository repository;
    private final CajaVirtualRepository cajaVirtualRepository;
    private final SucursalRepository sucursalRepository;

    @Transactional(readOnly = true)
    public List<Sucursal> adicionales(Long cajaVirtualId) {
        return repository.sucursalesAdicionales(cajaVirtualId);
    }

    /**
     * Deja como adicionales exactamente las sucursales pedidas: agrega las que faltan y quita las que
     * sobran. La sucursal propia de la caja se ignora (ya la cubre) y los repetidos cuentan una vez.
     */
    @Transactional
    public CajaVirtual guardar(Long cajaVirtualId, List<Long> sucursalIds, Usuario agregadoPor) {
        CajaVirtual caja = cajaVirtualRepository.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja no encontrada: " + cajaVirtualId));
        Long propia = caja.getSucursal() != null ? caja.getSucursal().getId() : null;

        Set<Long> pedidas = new LinkedHashSet<>();
        if (sucursalIds != null) {
            for (Long id : sucursalIds) {
                if (id != null && !id.equals(propia)) pedidas.add(id);
            }
        }

        for (CajaVirtualSucursal actual : repository.findByCajaVirtualIdOrderByIdAsc(cajaVirtualId)) {
            // Lo que ya está y sigue pedido no se toca; lo que ya no se pide se quita.
            if (!pedidas.remove(actual.getSucursal().getId())) repository.delete(actual);
        }
        for (Long id : pedidas) {
            Sucursal sucursal = sucursalRepository.findById(id)
                    .orElseThrow(() -> new GraphQLException("Sucursal no encontrada: " + id));
            CajaVirtualSucursal nueva = new CajaVirtualSucursal();
            nueva.setCajaVirtual(caja);
            nueva.setSucursal(sucursal);
            nueva.setAgregadoPor(agregadoPor);
            repository.save(nueva);
        }
        return caja;
    }
}
