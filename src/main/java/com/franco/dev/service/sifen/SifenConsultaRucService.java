package com.franco.dev.service.sifen;

import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.beans.response.RespuestaConsultaRUC;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Consulta de RUC en SIFEN (solo lectura: no emite ni registra nada en la SET).
 *
 * Estaba dentro de {@link SifenService}; se separo para que ClienteService la use sin depender de
 * SifenService, que a su vez depende de ClienteService y FacturaLegalService (ciclo de beans, issue
 * #651). El codigo de consultaRuc y mapearConsultaRuc es el mismo que tenia SifenService.
 *
 * Existe solo con sifen.enabled=true, igual que SifenService.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sifen.enabled", havingValue = "true", matchIfMissing = false)
public class SifenConsultaRucService {

    private final SifenConfig sifenConfig;

    public SifenConsultaRucService(SifenConfig sifenConfig) {
        this.sifenConfig = sifenConfig;
    }

    /**
     * Consulta un RUC en SIFEN.
     * 
     * @param ruc RUC a consultar (solo números, sin guiones ni dígito verificador)
     * @return ConsultaRucResponse con los datos del contribuyente, o null si hay error
     */
    public com.franco.dev.graphql.personas.ConsultaRucResponse consultaRuc(String ruc) {
        if (sifenConfig == null) {
            log.warn("SIFEN no está configurado");
            return null;
        }

        try {
            // Normalizar RUC (solo números)
            String rucNormalizado = ruc.replaceAll("[^0-9]", "");
            if (rucNormalizado.isEmpty()) {
                log.warn("RUC inválido: {}", ruc);
                return null;
            }

            log.info("🔍 Consultando RUC en SIFEN: {}", rucNormalizado);

            // Realizar consulta a SIFEN
            RespuestaConsultaRUC respuesta = Sifen.consultaRUC(rucNormalizado, sifenConfig);

            // Mapear respuesta a DTO
            return mapearConsultaRuc(respuesta);

        } catch (SifenException e) {
            log.error("Error de SIFEN al consultar RUC {}: {}", ruc, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Error inesperado al consultar RUC {}: {}", ruc, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Mapea la respuesta de SIFEN a nuestro DTO ConsultaRucResponse.
     */
    private com.franco.dev.graphql.personas.ConsultaRucResponse mapearConsultaRuc(
            RespuestaConsultaRUC respuesta) {
        if (respuesta == null) {
            return null;
        }

        com.franco.dev.graphql.personas.ConsultaRucResponse dto = 
            new com.franco.dev.graphql.personas.ConsultaRucResponse();

        // Código de respuesta de SIFEN
        dto.setCodigoRespuesta(respuesta.getdCodRes());
        dto.setMensajeRespuesta(respuesta.getdMsgRes());
        dto.setMensajeProcesamiento(respuesta.getdMsgRes());
        dto.setMensajeValidacion(respuesta.getdMsgRes());

        // Verificar si fue exitoso (código 0300 = éxito)
        boolean exito = "0300".equals(respuesta.getdCodRes());
        dto.setProcesamientoCorrecto(exito);
        dto.setValidacionCorrecta(exito);

        // Extraer datos del contribuyente
        // Nota: La estructura exacta depende de la versión de roshka-sifen
        // Si getxContRUC() retorna un objeto, acceder a sus propiedades
        try {
            Object datosObj = respuesta.getxContRUC();
            if (datosObj != null) {
                // Usar reflexión para acceder a los campos si es necesario
                // Por ahora, intentar métodos comunes
                try {
                    java.lang.reflect.Method getRuc = datosObj.getClass().getMethod("getdRUCCons");
                    java.lang.reflect.Method getRaz = datosObj.getClass().getMethod("getdRazCons");
                    java.lang.reflect.Method getEstCons = datosObj.getClass().getMethod("getdDesEstCons");
                    java.lang.reflect.Method getCodEstCons = datosObj.getClass().getMethod("getdCodEstCons");
                    java.lang.reflect.Method getRucFactElec = datosObj.getClass().getMethod("getdRUCFactElec");

                    String rucRespuesta = (String) getRuc.invoke(datosObj);
                    dto.setRuc(rucRespuesta);
                    dto.setRazonSocial((String) getRaz.invoke(datosObj));
                    String estadoCons = (String) getEstCons.invoke(datosObj);
                    dto.setEstadoContribuyente(estadoCons);
                    dto.setEstado(estadoCons);
                    dto.setCodigoEstadoContribuyente((String) getCodEstCons.invoke(datosObj));
                    dto.setEsFacturadorElectronico((String) getRucFactElec.invoke(datosObj));
                    dto.setNombre((String) getRaz.invoke(datosObj));
                } catch (Exception e) {
                    log.warn("Error al extraer datos de respuesta SIFEN usando reflexión: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error al obtener datos del contribuyente de SIFEN: {}", e.getMessage());
        }

        return dto;
    }
}
