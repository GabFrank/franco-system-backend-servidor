package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPosRegion;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface FormatoTerminalPosRegionRepository
        extends HelperRepository<FormatoTerminalPosRegion, Long> {

    default Class<FormatoTerminalPosRegion> getEntityClass() {
        return FormatoTerminalPosRegion.class;
    }

    List<FormatoTerminalPosRegion> findByFormatoTerminalPosIdOrderByOrdenAscIdAsc(Long formatoTerminalPosId);

    /** Un campo, una region por formato: lo garantiza el indice unico de {@code V225.5}. */
    FormatoTerminalPosRegion findByFormatoTerminalPosIdAndCampo(Long formatoTerminalPosId, String campo);

    long countByFormatoTerminalPosId(Long formatoTerminalPosId);
}
