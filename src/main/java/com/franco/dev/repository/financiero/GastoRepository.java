package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface GastoRepository extends HelperRepository<Gasto, EmbebedPrimaryKey> {

    default Class<Gasto> getEntityClass() {
        return Gasto.class;
    }

    public List<Gasto> findBySucursalIdAndCreadoEnBetween(Long id, LocalDateTime inicio, LocalDateTime fin);

    public List<Gasto> findByCajaIdAndSucursalId(Long id, Long sucId);

}