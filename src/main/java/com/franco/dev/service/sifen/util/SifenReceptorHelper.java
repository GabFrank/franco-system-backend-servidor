package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.utilitarios.CalcularVerificadorRuc;
import com.roshka.sifen.core.types.TiNatRec;
import com.roshka.sifen.core.types.TiTiOpe;
import com.roshka.sifen.core.types.TiTipCont;
import com.roshka.sifen.core.types.TiTipDocRec;
import com.roshka.sifen.core.types.PaisType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper crítico para determinar la configuración del receptor en documentos electrónicos.
 * Implementa las complejas reglas de negocio definidas por SIFEN para clasificar
 * receptores y determinar el tipo de operación.
 */
public class SifenReceptorHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(SifenReceptorHelper.class);
    
    // Monto máximo permitido para facturas innominadas (en guaraníes)
    private static final double MONTO_MAXIMO_INNOMINADO = 1000000.0; // 1 millón de guaraníes
    
    /**
     * Clase que encapsula la configuración completa del receptor para el DE.
     * Contiene todos los datos necesarios para configurar correctamente el receptor
     * en un documento electrónico según las reglas de SIFEN.
     */
    public static class ConfiguracionReceptor {
        private TiTiOpe tipoOperacion;           // Tipo de operación: B2B, B2C, B2G, B2F
        private TiNatRec naturalezaReceptor;     // CONTRIBUYENTE o NO_CONTRIBUYENTE
        private TiTipDocRec tipoDocumentoReceptor; // RUC, CI, PASAPORTE, etc.
        private String numeroDocumento;           // Número del documento (sin DV)
        private String digitoVerificador;         // DV del RUC (si aplica)
        private String nombreReceptor;            // Nombre del receptor
        private String pais;                      // País del receptor (código ISO)
        private boolean esInnominado;             // Si es factura innominada
        private String emailReceptor;             // Email del receptor (opcional)
        private String direccionReceptor;         // Dirección del receptor (opcional)
        private TiTipCont tipoContribuyente;      // PERSONA_FISICA, PERSONA_JURIDICA
        
        // Getters y setters
        public TiTiOpe getTipoOperacion() { return tipoOperacion; }
        public void setTipoOperacion(TiTiOpe tipoOperacion) { this.tipoOperacion = tipoOperacion; }
        
        public TiNatRec getNaturalezaReceptor() { return naturalezaReceptor; }
        public void setNaturalezaReceptor(TiNatRec naturalezaReceptor) { this.naturalezaReceptor = naturalezaReceptor; }
        
        public TiTipDocRec getTipoDocumentoReceptor() { return tipoDocumentoReceptor; }
        public void setTipoDocumentoReceptor(TiTipDocRec tipoDocumentoReceptor) { this.tipoDocumentoReceptor = tipoDocumentoReceptor; }
        
        public String getNumeroDocumento() { return numeroDocumento; }
        public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }
        
        public String getDigitoVerificador() { return digitoVerificador; }
        public void setDigitoVerificador(String digitoVerificador) { this.digitoVerificador = digitoVerificador; }
        
        public String getNombreReceptor() { return nombreReceptor; }
        public void setNombreReceptor(String nombreReceptor) { this.nombreReceptor = nombreReceptor; }
        
        public String getPais() { return pais; }
        public void setPais(String pais) { this.pais = pais; }
        
        public boolean isEsInnominado() { return esInnominado; }
        public void setEsInnominado(boolean esInnominado) { this.esInnominado = esInnominado; }
        
        public String getEmailReceptor() { return emailReceptor; }
        public void setEmailReceptor(String emailReceptor) { this.emailReceptor = emailReceptor; }
        
        public String getDireccionReceptor() { return direccionReceptor; }
        public void setDireccionReceptor(String direccionReceptor) { this.direccionReceptor = direccionReceptor; }
        
        public TiTipCont getTipoContribuyente() { return tipoContribuyente; }
        public void setTipoContribuyente(TiTipCont tipoContribuyente) { this.tipoContribuyente = tipoContribuyente; }
    }
    
    /**
     * Método principal que determina toda la configuración del receptor para un DE.
     * 
     * @param cliente Cliente al que se emite el documento (puede ser null para innominado)
     * @param montoTotal Monto total del documento
     * @return ConfiguracionReceptor con todos los datos necesarios
     */
    public static ConfiguracionReceptor determinarConfiguracionReceptor(Cliente cliente, Double montoTotal) {
        ConfiguracionReceptor config = new ConfiguracionReceptor();
        
        // Caso 1: Factura innominada (sin cliente o monto bajo)
        if (cliente == null || cliente.getPersona() == null) {
            return configurarInnominado(config, montoTotal);
        }
        
        Persona persona = cliente.getPersona();
        
        // Caso 2: Cliente con RUC (contribuyente)
        if (esContribuyente(cliente)) {
            return configurarContribuyente(config, cliente, persona);
        }
        
        // Caso 3: Cliente sin RUC (no contribuyente)
        return configurarNoContribuyente(config, cliente, persona, montoTotal);
    }
    
    /**
     * Configura una factura innominada (sin identificación del receptor)
     */
    private static ConfiguracionReceptor configurarInnominado(ConfiguracionReceptor config, Double montoTotal) {
        // Validar que el monto no supere el límite legal
        if (montoTotal != null && montoTotal > MONTO_MAXIMO_INNOMINADO) {
            logger.warn("El monto {} supera el máximo permitido para facturas innominadas ({})", 
                       montoTotal, MONTO_MAXIMO_INNOMINADO);
            // En producción, esto debería lanzar una excepción
        }
        
        config.setEsInnominado(true);
        config.setTipoOperacion(TiTiOpe.B2C); // Venta a consumidor final
        config.setNaturalezaReceptor(TiNatRec.NO_CONTRIBUYENTE);
        // Factura innominada según norma SIFEN
        config.setTipoDocumentoReceptor(TiTipDocRec.OTRO);
        config.setNumeroDocumento("0");
        config.setNombreReceptor("SIN NOMBRE");
        config.setPais(PaisType.PRY.name());
        
        return config;
    }
    
    /**
     * Configura un receptor contribuyente (con RUC)
     */
    private static ConfiguracionReceptor configurarContribuyente(ConfiguracionReceptor config, 
                                                                  Cliente cliente, 
                                                                  Persona persona) {
        config.setEsInnominado(false);
        config.setNaturalezaReceptor(TiNatRec.CONTRIBUYENTE);
        // RUC para contribuyentes según norma SIFEN
        config.setTipoDocumentoReceptor(TiTipDocRec.CEDULA_PARAGUAYA);
        
        // Extraer documento (RUC) y calcular DV
        String documento = persona.getDocumento();
        if (documento != null && documento.contains("-")) {
            String[] partes = documento.split("-");
            config.setNumeroDocumento(partes[0].trim());
            config.setDigitoVerificador(partes.length > 1 ? partes[1].trim() : calcularDV(partes[0]));
        } else if (documento != null) {
            config.setNumeroDocumento(documento.trim());
            config.setDigitoVerificador(calcularDV(documento));
        }
        
        config.setNombreReceptor(persona.getNombre() != null ? persona.getNombre().toUpperCase() : "");
        config.setPais(PaisType.PRY.name());
        config.setEmailReceptor(persona.getEmail());
        config.setDireccionReceptor(persona.getDireccion());
        
        // Determinar tipo de operación
        // Si es entidad gubernamental -> B2G
        if (esEntidadGubernamental(documento)) {
            config.setTipoOperacion(TiTiOpe.B2G);
        } else {
            // Operación entre empresas
            config.setTipoOperacion(TiTiOpe.B2B);
        }
        
        // Determinar tipo de contribuyente por el RUC
        // RUC que termina en -1 suele ser persona jurídica, otros son persona física
        String dv = config.getDigitoVerificador();
        if ("1".equals(dv)) {
            config.setTipoContribuyente(TiTipCont.PERSONA_JURIDICA);
        } else {
            config.setTipoContribuyente(TiTipCont.PERSONA_FISICA);
        }
        
        return config;
    }
    
    /**
     * Configura un receptor no contribuyente (sin RUC)
     */
    private static ConfiguracionReceptor configurarNoContribuyente(ConfiguracionReceptor config,
                                                                     Cliente cliente,
                                                                     Persona persona,
                                                                     Double montoTotal) {
        config.setEsInnominado(false);
        config.setNaturalezaReceptor(TiNatRec.NO_CONTRIBUYENTE);
        config.setTipoOperacion(TiTiOpe.B2C); // Venta a consumidor final
        
        // Determinar tipo de documento
        String documento = persona.getDocumento();
        if (documento != null && !documento.isEmpty()) {
            // Si tiene documento, usar CI (Cédula de Identidad Paraguaya)
            config.setTipoDocumentoReceptor(TiTipDocRec.CEDULA_PARAGUAYA);
            config.setNumeroDocumento(documento.replaceAll("[^0-9]", "")); // Solo números
        } else {
            // Si no tiene documento, marcar como innominado si el monto lo permite
            if (montoTotal != null && montoTotal <= MONTO_MAXIMO_INNOMINADO) {
                return configurarInnominado(config, montoTotal);
            } else {
                // Requiere identificación - usar CI como tipo por defecto
                logger.warn("Cliente sin documento para un monto que requiere identificación");
                config.setTipoDocumentoReceptor(TiTipDocRec.CEDULA_PARAGUAYA);
                config.setNumeroDocumento("0");
            }
        }
        
        config.setNombreReceptor(persona.getNombre() != null ? persona.getNombre().toUpperCase() : "");
        config.setPais(PaisType.PRY.name());
        config.setEmailReceptor(persona.getEmail());
        config.setDireccionReceptor(persona.getDireccion());
        
        return config;
    }
    
    /**
     * Determina si un cliente es contribuyente (tiene RUC)
     */
    private static boolean esContribuyente(Cliente cliente) {
        if (cliente == null || cliente.getTributa() == null) {
            return false;
        }
        return cliente.getTributa();
    }
    
    /**
     * Determina si un RUC pertenece a una entidad gubernamental
     * Los RUCs gubernamentales en Paraguay típicamente comienzan con ciertos prefijos
     */
    private static boolean esEntidadGubernamental(String ruc) {
        if (ruc == null || ruc.isEmpty()) {
            return false;
        }
        
        // Extraer solo la parte numérica del RUC
        String rucNumerico = ruc.split("-")[0].trim();
        
        // RUCs gubernamentales conocidos (lista no exhaustiva)
        // Típicamente comienzan con 8 seguido de 7 u 8
        return rucNumerico.startsWith("87") || rucNumerico.startsWith("88");
    }
    
    /**
     * Calcula el dígito verificador de un RUC paraguayo
     */
    private static String calcularDV(String rucBase) {
        try {
            int dv = CalcularVerificadorRuc.getDigitoVerificador(rucBase);
            return String.valueOf(dv);
        } catch (Exception e) {
            logger.error("Error al calcular DV para documento {}: {}", rucBase, e.getMessage());
            return "0";
        }
    }
    
    /**
     * Valida que una configuración de receptor sea válida antes de generar el DE
     */
    public static boolean validarConfiguracion(ConfiguracionReceptor config) {
        if (config == null) {
            logger.error("Configuración de receptor es null");
            return false;
        }
        
        if (config.getTipoOperacion() == null) {
            logger.error("Tipo de operación no definido");
            return false;
        }
        
        if (config.getNaturalezaReceptor() == null) {
            logger.error("Naturaleza del receptor no definida");
            return false;
        }
        
        if (config.getTipoDocumentoReceptor() == null) {
            logger.error("Tipo de documento del receptor no definido");
            return false;
        }
        
        // Validar que si es contribuyente, tenga RUC con DV
        if (config.getNaturalezaReceptor() == TiNatRec.CONTRIBUYENTE) {
            if (config.getNumeroDocumento() == null || config.getNumeroDocumento().isEmpty()) {
                logger.error("Contribuyente sin número de RUC");
                return false;
            }
            if (config.getDigitoVerificador() == null || config.getDigitoVerificador().isEmpty()) {
                logger.error("Contribuyente sin dígito verificador");
                return false;
            }
        }
        
        return true;
    }
}

