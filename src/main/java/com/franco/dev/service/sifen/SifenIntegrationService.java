package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.LoteDEService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.franco.dev.domain.personas.Usuario;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de integración para conectar automáticamente el flujo de ventas con SIFEN.
 * Maneja la creación automática de documentos electrónicos y su envío según configuración.
 */
@Slf4j
@Service
public class SifenIntegrationService {

    @Autowired
    private SifenService sifenService;

    @Autowired
    private FacturaLegalService facturaLegalService;

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    @Autowired
    private LoteDEService loteDEService;

    @Value("${sifen.auto-create-de:true}")
    private boolean autoCreateDE;

    @Value("${sifen.auto-send-lote:true}")
    private boolean autoSendLote;

    @Value("${sifen.lote-size:10}")
    private int loteSize;

    /**
     * Orquesta la creación del Documento Electrónico.
     * Llama al SifenService para crear el DE y luego decide si añadirlo a un lote
     * inmediatamente o esperar al job programado.
     *
     * @param factura El ID de la factura legal a procesar.
     */
    @Transactional
    public void crearDEAutomatico(FacturaLegal factura) {
        if (factura.getId() == null || factura.getSucursalId() == null) {
            log.error("ID de factura o sucursal es nulo. No se puede procesar.");
            return;
        }

        try {
            // Verificar si ya existe un DE para esta factura
            Optional<DocumentoElectronico> existente = documentoElectronicoService.findByFacturaLegalId(factura.getId());
            if (existente.isPresent()) {
                log.warn("Intento de crear un DE duplicado para la factura ID {}. Omitiendo.", factura.getId());
                return;
            }

            log.info("Iniciando creación automática de DE para factura ID: {}", factura.getId());
            DocumentoElectronico de = sifenService.crearDocumentoElectronico(factura);
            log.info("DE creado exitosamente con CDC: {}. Procesando para envío...", de.getCdc());

            if (autoSendLote) {
                procesarEnvioAutomatico(de);
            }

        } catch (Exception e) {
            log.error("Error catastrófico al crear DE para factura ID {}: {}", factura.getId(), e.getMessage(), e);
            // Aquí se podría registrar el fallo en una tabla de reintentos
        }
    }

    /**
     * Maneja la lógica de agrupar DEs en lotes y enviarlos automáticamente.
     * Si hay un lote abierto, añade el DE. Si no, crea uno nuevo.
     * Si el lote alcanza el tamaño máximo, lo envía.
     *
     * @param de El documento electrónico a procesar.
     */
    private void procesarEnvioAutomatico(DocumentoElectronico de) {
        // Lógica para encontrar o crear un lote pendiente
        // Añadir el DE al lote
        // Si el lote está lleno, enviarlo
    }

    /**
     * Busca un lote abierto y pendiente de envío.
     * @return Un Optional conteniendo el lote si se encuentra.
     */
    private Optional<LoteDE> findLoteAbierto() {
        List<LoteDE> lotesPendientes = loteDEService.findByEstado(EstadoLoteDE.PENDIENTE_ENVIO);
        return lotesPendientes.stream()
                .filter(lote -> {
                    List<DocumentoElectronico> des = documentoElectronicoService.findByLoteDeIdList(lote.getId());
                    return des.size() < loteSize;
                })
                .findFirst();
    }

    /**
     * Crea un nuevo lote y lo guarda en la base de datos.
     * @param usuario El usuario que crea el lote.
     * @return El nuevo lote creado.
     */
    private LoteDE crearNuevoLote(Usuario usuario) {
        LoteDE nuevoLote = new LoteDE();
        nuevoLote.setEstado(EstadoLoteDE.PENDIENTE_ENVIO);
        nuevoLote.setUsuario(usuario);
        nuevoLote.setCreadoEn(LocalDateTime.now());
        return loteDEService.save(nuevoLote);
    }
}
