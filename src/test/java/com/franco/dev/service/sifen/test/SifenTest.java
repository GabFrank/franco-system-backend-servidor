package com.franco.dev.service.sifen.test;

import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.service.financiero.LoteDEService;
import com.franco.dev.service.sifen.SifenService;
import com.roshka.sifen.core.beans.response.RespuestaConsultaDE;
import com.roshka.sifen.core.beans.response.RespuestaConsultaLoteDE;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import org.springframework.test.annotation.Commit;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
@Transactional
public class SifenTest {

    @Autowired
    private SifenService sifenService;

    @Autowired
    private LoteDEService loteDEService;

    @Autowired
    private FacturaLegalService facturaLegalService;


    @Autowired
    private TimbradoDetalleService timbradoDetalleService;

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;


    /**
     * Prueba el flujo completo:
     * 1. Selecciona una FacturaLegal existente de la sucursal 24.
     * 2. Le asigna un nuevo número de factura correlativo.
     * 3. Crea el Documento Electrónico (DE) correspondiente.
     * 4. Crea un LoteDE, vincula el DE y lo envía a SIFEN.
     */
    @Test
    @Transactional
    @Commit // Para persistir los datos generados y poder verificarlos en la BD
    void flujoCompletoCrearYEnviarDELoteTest() {
        log.info("🚀 INICIANDO TEST DE FLUJO COMPLETO: Seleccionar Factura -> Crear DE -> Enviar Lote");

        try {
            // =====================================================
            // PASO 1: Seleccionar una Factura Legal existente de la sucursal 24
            // =====================================================
            log.info("PASO 1: Buscando una Factura Legal existente de la sucursal 24...");

            LocalDateTime fechaFin = LocalDateTime.now();
            LocalDateTime fechaInicio = LocalDateTime.now().minusDays(30); // buscar en los últimos 30 días

            List<FacturaLegal> facturasPage = facturaLegalService.getRepository().findByCreadoEnBetweenAndSucursalId(fechaInicio, fechaFin, 24L);

            FacturaLegal factura = null;

            FacturaLegal facturaSeleccionada = factura != null ? factura : facturasPage.stream()
                .filter(f -> f.getVenta() != null && !documentoElectronicoService.findByFacturaLegalId(f.getId(), f.getSucursalId()).isPresent())
                .findFirst()
                .orElse(null);

            if (facturaSeleccionada == null) {
                throw new IllegalStateException("No se encontraron facturas legales recientes sin Documento Electrónico en la sucursal 24 para usar en el test");
            }

            log.info("✅ Factura Legal ID {} seleccionada para el test.", facturaSeleccionada.getId());

            // =====================================================
            // PASO 2: Respetar la numeración (asignar nuevo número)
            // =====================================================
            log.info("PASO 2: Asignando nueva numeración a la factura...");
            TimbradoDetalle timbradoDetalle = facturaSeleccionada.getTimbradoDetalle();
            if (timbradoDetalle == null || !Boolean.TRUE.equals(timbradoDetalle.getTimbrado().getIsElectronico())) {
                log.warn("Factura no tiene timbrado electrónico. Buscando uno activo para la sucursal 24...");
                timbradoDetalle = timbradoDetalleService.findBySucursalId(24L).stream()
                        .filter(td -> td.getActivo() && td.getTimbrado() != null && Boolean.TRUE.equals(td.getTimbrado().getIsElectronico()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No se encontró un TimbradoDetalle electrónico y activo para la sucursal 24"));
                facturaSeleccionada.setTimbradoDetalle(timbradoDetalle);
            }

            Long siguienteNumero = (timbradoDetalle.getNumeroActual() != null ? timbradoDetalle.getNumeroActual() : 0) + 1;
            facturaSeleccionada.setNumeroFactura(siguienteNumero.intValue());
            facturaLegalService.save(facturaSeleccionada); // Guardar el nuevo número en la factura

            // Actualizar número actual del timbrado
            timbradoDetalle.setNumeroActual(siguienteNumero);
            timbradoDetalleService.save(timbradoDetalle);
            log.info("✅ Factura actualizada con el número: {}", siguienteNumero);


            // =====================================================
            // PASO 3: Crear Documento Electrónico
            // =====================================================
            log.info("PASO 3: Creando Documento Electrónico desde la factura...");
            com.franco.dev.domain.financiero.DocumentoElectronico de = sifenService.crearDocumentoElectronico(facturaSeleccionada);
            assert de != null;
            assert de.getEstado() == EstadoDE.PENDIENTE;
            log.info("✅ Documento Electrónico ID {} creado con CDC: {}", de.getId(), de.getCdc());

            // =====================================================
            // PASO 4: Crear Lote y Enviar a SIFEN
            // =====================================================
            log.info("PASO 4: Creando y enviando lote con el DE pendiente...");

            LoteDE lote = sifenService.crearLote();
            sifenService.vincularDocumentosALote(lote, Collections.singletonList(de));
            sifenService.enviarLote(lote);

            // Recargar el lote para verificar el estado
            LoteDE loteEnviado = loteDEService.findByIdAndSucursalId(lote.getId(), lote.getSucursalId())
                    .orElseThrow(() -> new IllegalStateException("El lote recién creado no se encontró en la BD"));

            assert loteEnviado.getEstado() == EstadoLoteDE.EN_PROCESO;
            assert loteEnviado.getProtocolo() != null && !loteEnviado.getProtocolo().isEmpty();
            log.info("✅ Lote ID {} enviado exitosamente a SIFEN.", loteEnviado.getId());
            log.info("   - Estado: {}", loteEnviado.getEstado());
            log.info("   - Protocolo: {}", loteEnviado.getProtocolo());

            // Paso 5: Consultar el lote
            log.info("PASO 5: Consultando el lote...");
            RespuestaConsultaLoteDE respuestaConsultaLote = sifenService.consultaLotePorProtocolo(loteEnviado.getProtocolo());
            log.info("Respuesta de SIFEN para consulta de lote: Código {}, Mensaje: {}", respuestaConsultaLote.getdCodResLot(), respuestaConsultaLote.getdMsgResLot());
            log.info("Respuesta bruta: {}", respuestaConsultaLote.getRespuestaBruta());
            assert respuestaConsultaLote.getRespuestaBruta() != null && !respuestaConsultaLote.getRespuestaBruta().isEmpty();
            log.info("✅ Lote ID {} consultado exitosamente a SIFEN.", loteEnviado.getId());
            log.info("   - Estado: {}", loteEnviado.getEstado());
            log.info("   - Protocolo: {}", loteEnviado.getProtocolo());

        } catch (Exception e) {
            log.error("❌ Error durante el test de flujo completo", e);
            throw new RuntimeException(e);
        }

        log.info("🎉 TEST DE FLUJO COMPLETO FINALIZADO EXITOSAMENTE");
    }

    /**
     * Prueba la consulta de un Documento Electrónico (DE) por su CDC.
     * Es necesario reemplazar 'CDC_DE_PRUEBA' con un CDC válido del ambiente de SIFEN que se esté probando.
     */
    @Test
    void consultaDETest() {
        log.info("Iniciando prueba de consulta de Documento Electrónico (DE)...");

        // TODO: Reemplazar con un CDC válido para la prueba
        String cdcPrueba = "0180099482001001000007922025102210087356262"; 

        if ("CDC_DE_PRUEBA".equals(cdcPrueba)) {
            log.warn("ATENCIÓN: El CDC de prueba no ha sido modificado. La prueba no se ejecutará.");
            return;
        }

        try {
            RespuestaConsultaDE respuesta = sifenService.consultarDE(cdcPrueba);
            log.info("Respuesta de SIFEN para consulta DE: Código {}, Mensaje: {}", respuesta.getdCodRes(), respuesta.getdMsgRes());
            
            // Aquí puedes agregar aserciones para verificar la respuesta
            // Ejemplo: assertNotNull(respuesta);
            
        } catch (Exception e) {
            log.error("Error durante la prueba de consulta de DE", e);
        }

        log.info("Prueba de consulta de DE finalizada.");
    }

    /**
     * Prueba la consulta de un Lote de Documentos Electrónicos.
     * Es necesario reemplazar 'PROTOCOLO_LOTE_PRUEBA' con un protocolo de lote válido.
     */
    @Test
    void consultaLoteTest() {
        log.info("Iniciando prueba de consulta de Lote de Documentos Electrónicos...");

        String protocolo = "10786082195263820";

        RespuestaConsultaLoteDE respuesta = sifenService.consultaLotePorProtocolo(protocolo);
        log.info("Respuesta de SIFEN para consulta de lote: Código {}, Mensaje: {}", respuesta.getdCodResLot(), respuesta.getdMsgResLot());
        log.info("Respuesta bruta: {}", respuesta.getRespuestaBruta());
    }
}
