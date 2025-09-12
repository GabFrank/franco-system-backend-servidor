package com.franco.dev.service.sifen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.service.sifen.dto.response.ConsultaRucResponse;
import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.beans.response.RespuestaConsultaRUC;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SifenService {

    public ConsultaRucResponse consultaRuc(String ruc) {
        try {
            String rucSinDv = cleanRuc(ruc);
            log.info("Consultando RUC: {} (enviado como: {})", ruc, rucSinDv);
            RespuestaConsultaRUC respuestaSifen = Sifen.consultaRUC(rucSinDv);
            return mapToConsultaRucResponse(respuestaSifen, ruc);
        } catch (SifenException e) {
            log.error("Error al consultar RUC en SIFEN: {}", e.getMessage(), e);
            return createErrorResponse(e);
        }
    }

    private String cleanRuc(String ruc) {
        if (ruc == null) return "";
        String cleanRuc = ruc.replaceAll("[^0-9]", "");
        if (cleanRuc.length() > 1) {
            return cleanRuc.substring(0, cleanRuc.length() - 1);
        }
        return cleanRuc;
    }

    private ConsultaRucResponse mapToConsultaRucResponse(RespuestaConsultaRUC r, String rucOriginal) {
        ConsultaRucResponse dto = new ConsultaRucResponse();
        dto.setProcesamientoCorrecto("0502".equals(r.getdCodRes()));
        dto.setCodigoRespuesta(r.getdCodRes());
        dto.setMensajeRespuesta(r.getdMsgRes());
        dto.setRuc(rucOriginal);
        if (r.getxContRUC() != null) {
            dto.setRazonSocial(r.getxContRUC().getdRazCons());
            dto.setEstadoContribuyente(r.getxContRUC().getdDesEstCons());
            dto.setCodigoEstadoContribuyente(r.getxContRUC().getdCodEstCons());
            dto.setEsFacturadorElectronico(r.getxContRUC().getdRUCFactElec());
        }
        return dto;
    }

    private ConsultaRucResponse createErrorResponse(SifenException e) {
        ConsultaRucResponse dto = new ConsultaRucResponse();
        dto.setProcesamientoCorrecto(false);
        dto.setCodigoRespuesta("999");
        dto.setMensajeRespuesta("Error de comunicación con SIFEN: " + e.getMessage());
        return dto;
    }
    
    // Método main para pruebas locales
    public static void main(String[] args) {
        try {
            System.out.println("Iniciando prueba de consulta RUC SIFEN...");
            
            // Crear configuración manualmente para pruebas
            SifenConfig config = new SifenConfig(
                SifenConfig.TipoAmbiente.DEV,
                "0001", // CSC ID
                "D37561586c1CAd69A2e7747E73f9F03B", // CSC
                SifenConfig.TipoCertificadoCliente.PFX,
                "/home/franco/Documents/franco-sa.pfx",
                "fasa1701"
            );
            
            Sifen.setSifenConfig(config);
            System.out.println("Configuración cargada correctamente.");

            SifenService service = new SifenService();
            String rucDePrueba = "80099482-5";
            ConsultaRucResponse respuesta = service.consultaRuc(rucDePrueba);

            ObjectMapper objectMapper = new ObjectMapper();
            System.out.println("Respuesta mapeada (JSON): " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(respuesta));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
