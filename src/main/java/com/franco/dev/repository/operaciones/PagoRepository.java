package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.repository.HelperRepository;

public interface PagoRepository extends HelperRepository<Pago, Long> {
    default Class<Pago> getEntityClass() {
        return Pago.class;
    }
}
