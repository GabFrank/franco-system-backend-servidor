package com.franco.dev.service.ia;

import com.franco.dev.domain.empresarial.ConfiguracionSistema;
import com.franco.dev.service.empresarial.ConfiguracionSistemaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * SMOKE TEST EN VIVO contra la API real de OpenAI. NO corre en CI ni en el build normal:
 * solo se ejecuta si esta seteada la variable de entorno OPENAI_API_KEY (cuesta tokens reales).
 *
 * Prueba el pipeline de extraccion (sin DB ni app completa) con una factura real, incluyendo
 * el caso multipagina y el guardrail de cuadre de totales.
 *
 * Como correrlo:
 *   export OPENAI_API_KEY=sk-...                       # la key real
 *   export IMPORT_SAMPLE_FILES=/ruta/factura.pdf       # 1 PDF (multipagina interno) o
 *   export IMPORT_SAMPLE_FILES=/ruta/p1.jpg,/ruta/p2.jpg   # varias fotos (paginas)
 *   export IMPORT_SAMPLE_MIME=application/pdf          # o image/jpeg, image/png
 *   ./mvnw -o test -Dflyway.skip=true -Dtest=OpenAiVisionLiveTest
 */
@ExtendWith(MockitoExtension.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiVisionLiveTest {

    @Mock
    private ConfiguracionSistemaService configService;

    @Test
    void extraerFacturaReal_imprimeResultadoYControlaTotales() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String modelo = System.getenv().getOrDefault("IMPORT_SAMPLE_MODEL", "gpt-4o");
        String samples = System.getenv("IMPORT_SAMPLE_FILES");
        String mime = System.getenv().getOrDefault("IMPORT_SAMPLE_MIME", "image/jpeg");
        assertNotNull(samples, "Seteá IMPORT_SAMPLE_FILES con la ruta de la factura (coma-separado si son varias)");

        // -- wiring del servicio con la key real --
        OpenAiVisionService vision = new OpenAiVisionService();
        ReflectionTestUtils.setField(vision, "configService", configService);
        ReflectionTestUtils.setField(vision, "baseUrl", "https://api.openai.com");
        ReflectionTestUtils.setField(vision, "timeoutMs", 240000);
        lenient().when(configService.getDecrypted("openai.api_key")).thenReturn(Optional.of(apiKey));
        lenient().when(configService.findByClave("openai.modelo"))
                .thenReturn(Optional.of(new ConfiguracionSistema("openai.modelo", modelo, "", false, LocalDateTime.now())));
        lenient().when(configService.findByClave("openai.prompt_adicional"))
                .thenReturn(Optional.empty());

        // -- armar las paginas a partir de los archivos --
        PdfConverterService pdf = new PdfConverterService();
        List<OpenAiVisionService.ImagenParaAnalisis> imagenes = new ArrayList<>();
        for (String ruta : samples.split(",")) {
            byte[] bytes = Files.readAllBytes(Paths.get(ruta.trim()));
            if (mime.contains("pdf")) {
                for (byte[] page : pdf.todasLasPaginasComoJpeg(bytes)) {
                    imagenes.add(new OpenAiVisionService.ImagenParaAnalisis(page, "image/jpeg"));
                }
            } else {
                imagenes.add(new OpenAiVisionService.ImagenParaAnalisis(bytes, mime));
            }
        }
        System.out.println("\n========== SMOKE TEST OpenAI ==========");
        System.out.println("Modelo: " + modelo + " | paginas enviadas: " + imagenes.size());

        // -- llamada real --
        OpenAiVisionService.Resultado r = vision.analizarImagenes(imagenes);

        System.out.println("Tokens: prompt=" + r.tokensPrompt + " respuesta=" + r.tokensRespuesta
                + " | modelo usado=" + r.modeloUsado);
        System.out.println("--- JSON crudo OpenAI ---");
        System.out.println(r.rawJson);

        FacturaIaResponse data = r.data;
        assertNull(data.getError(), "OpenAI marco el documento como invalido: " + data.getError());

        System.out.println("--- Cabecera extraida ---");
        System.out.println("emisorRuc=" + data.getEmisorRuc() + " emisorNombre=" + data.getEmisorNombre());
        System.out.println("numeroFactura=" + data.getNumeroFactura() + " timbrado=" + data.getTimbrado()
                + " esLegal=" + data.getEsLegal());
        System.out.println("fechaEmision=" + data.getFechaEmision() + " moneda=" + data.getMoneda()
                + " totalGeneral=" + data.getTotalGeneral());

        int n = data.getItems() != null ? data.getItems().size() : 0;
        System.out.println("--- Items: " + n + " ---");
        if (data.getItems() != null) {
            for (FacturaIaResponse.Item it : data.getItems()) {
                System.out.println("  [" + it.getCodigoProducto() + "] " + it.getNombreProducto()
                        + " | cant=" + it.getCantidad() + " pu=" + it.getPrecioUnitario()
                        + " desc=" + it.getDescuento() + " total=" + it.getTotalItem());
            }
        }

        // -- guardrail de totales sobre datos reales (misma logica que el preview) --
        BigDecimal suma = null;
        if (data.getItems() != null) {
            for (FacturaIaResponse.Item it : data.getItems()) {
                if (it.getTotalItem() != null) {
                    suma = (suma == null ? BigDecimal.ZERO : suma).add(it.getTotalItem());
                }
            }
        }
        BigDecimal total = data.getTotalGeneral();
        System.out.println("--- Control de totales ---");
        System.out.println("sumaItems=" + suma + " totalGeneral=" + total);
        if (total != null && suma != null) {
            BigDecimal tolerancia = total.abs().multiply(new BigDecimal("0.01")).max(BigDecimal.ONE);
            boolean cuadran = total.subtract(suma).abs().compareTo(tolerancia) <= 0;
            System.out.println("cuadran=" + cuadran + (cuadran ? "" : "  <-- REVISAR (¿faltan paginas?)"));
        } else {
            System.out.println("cuadran=null (no comparable)");
        }
        System.out.println("=======================================\n");

        // El test pasa si OpenAI devolvio una factura con al menos 1 item legible.
        assertTrue(n > 0, "OpenAI no extrajo ningun item — revisar prompt/calidad de imagen");
    }
}
