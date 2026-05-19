package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PreGastoDetalleFinanzas;
import com.franco.dev.repository.financiero.PreGastoDetalleFinanzasRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PreGastoDetalleFinanzasService
        extends CrudService<PreGastoDetalleFinanzas, PreGastoDetalleFinanzasRepository, Long> {

    private final PreGastoDetalleFinanzasRepository repository;

    @Override
    public PreGastoDetalleFinanzasRepository getRepository() {
        return repository;
    }

    public List<PreGastoDetalleFinanzas> findByPreGastoIdAndSucursalId(Long preGastoId, Long sucursalId) {
        return repository.findByPreGastoIdAndSucursalId(preGastoId, sucursalId);
    }
}
