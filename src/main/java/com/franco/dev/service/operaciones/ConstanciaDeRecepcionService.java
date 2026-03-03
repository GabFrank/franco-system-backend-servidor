package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.enums.EstadoConstancia;
import com.franco.dev.repository.operaciones.ConstanciaDeRecepcionItemRepository;
import com.franco.dev.repository.operaciones.ConstanciaDeRecepcionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.print.ConstanciaRecepcionPrintService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ConstanciaDeRecepcionService extends CrudService<ConstanciaDeRecepcion, ConstanciaDeRecepcionRepository, Long> {

    private final ConstanciaDeRecepcionRepository repository;
    private final ConstanciaDeRecepcionItemRepository itemRepository;
    private final ConstanciaRecepcionPrintService printService;

    @Override
    public ConstanciaDeRecepcionRepository getRepository() {
        return repository;
    }

    @Transactional
    public ConstanciaDeRecepcion generarConstancia(RecepcionMercaderia recepcion, List<RecepcionMercaderiaItem> items) {
        ConstanciaDeRecepcion constancia = new ConstanciaDeRecepcion();
        constancia.setRecepcionMercaderia(recepcion);
        constancia.setProveedor(recepcion.getProveedor());
        constancia.setSucursal(recepcion.getSucursalRecepcion());
        constancia.setFechaEmision(LocalDateTime.now());
        constancia.setUsuario(recepcion.getUsuario());
        constancia.setCodigoVerificacion(UUID.randomUUID().toString());
        constancia.setEstado(EstadoConstancia.EMITIDA);

        ConstanciaDeRecepcion saved = repository.save(constancia);

        for (RecepcionMercaderiaItem rmi : items) {
            if (rmi.getCantidadRecibida() == null || rmi.getCantidadRecibida() <= 0) continue;
            ConstanciaDeRecepcionItem ci = new ConstanciaDeRecepcionItem();
            ci.setConstanciaDeRecepcion(saved);
            ci.setProducto(rmi.getProducto());
            ci.setPresentacion(rmi.getPresentacionRecibida());
            ci.setCantidadRecibida(rmi.getCantidadRecibida());
            ci.setCantidadRechazadaFisico(rmi.getCantidadRechazada());
            itemRepository.save(ci);
        }

        return saved;
    }

    /**
     * Genera PDF de constancia de recepción (on demand, sin persistir constancia).
     * TODO: Analizar a futuro si conviene persistir ConstanciaDeRecepcion para snapshot inmutable, trazabilidad y reutilización del mismo documento.
     */
    public byte[] generarConstanciaRecepcionPDF(Long recepcionId) throws Exception {
        return printService.generarConstanciaRecepcionPDF(recepcionId);
    }
}

