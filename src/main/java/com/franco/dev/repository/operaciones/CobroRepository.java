package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.repository.HelperRepository;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

public interface CobroRepository extends HelperRepository<Cobro, EmbebedPrimaryKey> {
    default Class<Cobro> getEntityClass() {
        return Cobro.class;
    }

    Cobro findByIdAndSucursalId(Long id, Long sucId);

    Boolean deleteByIdAndSucursalId(Long id, Long sucId);
}

