# Guía de Implementación: Consulta de Cliente con SIFEN en el Servidor Central

## 1. Estructura General

En el servidor filial, la consulta a SIFEN se hace directamente porque es donde se emiten las facturas. En el servidor central, puedes implementarla de dos formas:

### Opción A: Consulta Directa a SIFEN (Recomendada)
- El servidor central consulta SIFEN directamente
- Mantiene independencia del servidor filial
- Requiere configuración de SIFEN en el servidor central

### Opción B: Delegar al Servidor Filial
- El servidor central pide al servidor filial que consulte SIFEN
- Más simple si no quieres configurar SIFEN en el central
- Depende de la disponibilidad del servidor filial

## 2. Implementación - Opción A (Consulta Directa)

### 2.1. Servicio SIFEN

Crea un servicio similar al del servidor filial:

```java
@Service
@Slf4j
public class SifenService {
    
    private final SifenConfig sifenConfig;
    private final boolean sifenEnabled;
    
    // Constructor que inicializa la configuración de SIFEN
    // desde application.properties
    
    public boolean isSifenEnabled() {
        return sifenEnabled;
    }
    
    /**
     * Consulta un RUC en SIFEN
     * @param ruc RUC a consultar (solo números)
     * @return ConsultaRucResponse con los datos del contribuyente, o null si hay error
     */
    public ConsultaRucResponse consultaRuc(String ruc) {
        if (!sifenEnabled) {
            log.warn("SIFEN no está habilitado");
            return null;
        }
        
        try {
            // Normalizar RUC (solo números)
            String rucNormalizado = ruc.replaceAll("[^0-9]", "");
            if (rucNormalizado.isEmpty()) {
                log.warn("RUC inválido: {}", ruc);
                return null;
            }
            
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
     * Mapea la respuesta de SIFEN a nuestro DTO
     */
    private ConsultaRucResponse mapearConsultaRuc(RespuestaConsultaRUC respuesta) {
        if (respuesta == null) {
            return null;
        }
        
        ConsultaRucResponse dto = new ConsultaRucResponse();
        
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
        TxContRuc datos = respuesta.getxContRUC();
        if (datos != null) {
            String rucRespuesta = datos.getdRUCCons();
            dto.setRuc(rucRespuesta);
            dto.setRazonSocial(datos.getdRazCons());
            dto.setEstadoContribuyente(datos.getdDesEstCons());
            dto.setEstado(datos.getdDesEstCons());
            dto.setCodigoEstadoContribuyente(datos.getdCodEstCons());
            dto.setEsFacturadorElectronico(datos.getdRUCFactElec());
            
            // Calcular dígito verificador
            if (rucRespuesta != null) {
                Integer dv = CalcularVerificadorRuc.getDigitoVerificador(rucRespuesta);
                if (dv != null) {
                    dto.setDv(String.valueOf(dv));
                }
            }
            
            dto.setNombre(datos.getdRazCons());
        }
        
        return dto;
    }
}
```

### 2.2. DTO ConsultaRucResponse

Crea el mismo DTO que en el servidor filial:

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class ConsultaRucResponse extends SifenResponseBase {
    private String ruc;
    private String razonSocial;
    private String estadoContribuyente;
    private String codigoEstadoContribuyente;
    private String esFacturadorElectronico;
    private String mensajeProcesamiento;
    private String dv;
    private String estado;
    private String nombre;
    private String nombreFantasia;
    private String telefono;
    private String direccion;
    private Integer codigoEstablecimiento;
    private Boolean validacionCorrecta;
    private String mensajeValidacion;
}
```

### 2.3. Integración en ClienteService o ClienteGraphQL

En el método que busca/crea clientes:

```java
public Cliente buscarOCrearClientePorDocumento(String documento) {
    // 1. Buscar cliente existente en BD
    Cliente cliente = clienteRepository.findByPersonaDocumento(documento);
    if (cliente != null && Boolean.TRUE.equals(cliente.getVerificadoSet())) {
        return cliente; // Cliente ya existe y está verificado
    }
    
    // 2. Si no existe o no está verificado, consultar SIFEN
    if (!sifenService.isSifenEnabled()) {
        log.warn("SIFEN no está habilitado, no se puede verificar cliente");
        return cliente; // Retornar cliente existente si hay
    }
    
    ConsultaRucResponse respuestaSifen = sifenService.consultaRuc(documento);
    if (respuestaSifen == null) {
        log.warn("No se pudo obtener respuesta de SIFEN para documento: {}", documento);
        return cliente; // Retornar cliente existente si hay
    }
    
    // 3. Validar que sea contribuyente
    if (respuestaSifen.getRuc() == null || respuestaSifen.getRuc().trim().isEmpty()) {
        throw new BusinessException("El cliente no es contribuyente de SET");
    }
    
    // 4. Crear o actualizar Persona con datos de SIFEN
    Persona persona = cliente != null ? cliente.getPersona() : null;
    if (persona == null) {
        persona = personaRepository.findByDocumento(documento);
    }
    
    if (persona == null) {
        persona = new Persona();
        persona.setDocumento(documento);
    }
    
    // Actualizar datos desde SIFEN
    String nombreProcesado = formatearRazonSocial(respuestaSifen.getRazonSocial());
    if (nombreProcesado != null && !nombreProcesado.isEmpty()) {
        persona.setNombre(nombreProcesado.toUpperCase());
    }
    if (respuestaSifen.getDireccion() != null && !respuestaSifen.getDireccion().trim().isEmpty()) {
        persona.setDireccion(respuestaSifen.getDireccion().toUpperCase());
    }
    
    // Guardar persona
    persona = personaService.save(persona);
    
    // 5. Crear o actualizar Cliente
    if (cliente == null) {
        cliente = new Cliente();
        cliente.setPersona(persona);
        cliente.setCreadoEn(LocalDateTime.now());
    }
    
    cliente.setVerificadoSet(true);
    cliente.setTributa("ACTIVO".equalsIgnoreCase(respuestaSifen.getEstadoContribuyente()));
    
    // Parsear tipo contribuyente
    Integer tipoContribuyente = null;
    if (respuestaSifen.getCodigoEstadoContribuyente() != null) {
        try {
            tipoContribuyente = Integer.parseInt(respuestaSifen.getCodigoEstadoContribuyente().trim());
        } catch (NumberFormatException e) {
            log.warn("No se pudo parsear tipo contribuyente: {}", respuestaSifen.getCodigoEstadoContribuyente());
        }
    }
    cliente.setTipoContribuyente(tipoContribuyente);
    
    // Guardar cliente
    return clienteService.save(cliente);
}

/**
 * Formatea la razón social de SIFEN (apellido, nombre -> nombre apellido)
 */
private String formatearRazonSocial(String razonSocial) {
    if (razonSocial == null || razonSocial.trim().isEmpty()) {
        return null;
    }
    
    String valor = razonSocial.trim();
    int indiceComa = valor.indexOf(',');
    
    if (indiceComa < 0) {
        return valor; // No hay coma, retornar tal cual
    }
    
    String apellido = valor.substring(0, indiceComa).trim();
    String nombres = valor.substring(indiceComa + 1).trim();
    
    if (nombres.isEmpty()) {
        return valor.replace(",", " ").trim();
    }
    
    if (apellido.isEmpty()) {
        return nombres;
    }
    
    return nombres + " " + apellido;
}
```

## 3. Manejo de Respuestas y Errores

### 3.1. Casos de Éxito

```java
// Respuesta exitosa de SIFEN
ConsultaRucResponse respuesta = sifenService.consultaRuc("1234567");

if (respuesta != null && respuesta.getProcesamientoCorrecto()) {
    // Datos válidos
    String razonSocial = respuesta.getRazonSocial();
    String estado = respuesta.getEstadoContribuyente(); // "ACTIVO", "INACTIVO", etc.
    String ruc = respuesta.getRuc();
    // ... usar datos
}
```

### 3.2. Casos de Error

```java
// 1. SIFEN no habilitado
if (!sifenService.isSifenEnabled()) {
    // Manejar: usar datos locales o retornar error
}

// 2. Error de conexión o SIFEN
ConsultaRucResponse respuesta = sifenService.consultaRuc(ruc);
if (respuesta == null) {
    // Manejar: log error, retornar cliente existente si hay, o error
    log.error("No se pudo consultar SIFEN para RUC: {}", ruc);
}

// 3. No es contribuyente
if (respuesta != null && (respuesta.getRuc() == null || respuesta.getRuc().isEmpty())) {
    // Manejar: lanzar excepción o retornar error
    throw new BusinessException("El RUC no es contribuyente de SET");
}

// 4. Código de error de SIFEN
if (respuesta != null && !respuesta.getProcesamientoCorrecto()) {
    String mensaje = respuesta.getMensajeProcesamiento();
    // Manejar según el código de error
    log.warn("SIFEN retornó error: {}", mensaje);
}
```

## 4. Configuración

### 4.1. application.properties

```properties
# Habilitar/deshabilitar SIFEN
sifen.enabled=true

# Configuración de SIFEN (ambiente de producción o pruebas)
sifen.ambiente=PRODUCCION
sifen.idKiosco=tu-id-kiosco
sifen.claveKiosco=tu-clave-kiosco
sifen.certificado.path=/ruta/al/certificado.p12
sifen.certificado.password=password-del-certificado
```

### 4.2. Configuración de SIFEN

```java
@Configuration
public class SifenConfig {
    
    @Value("${sifen.enabled:false}")
    private boolean sifenEnabled;
    
    @Value("${sifen.ambiente:PRODUCCION}")
    private String ambiente;
    
    // ... otros valores
    
    @Bean
    public SifenConfig sifenConfig() {
        if (!sifenEnabled) {
            return null;
        }
        
        // Configurar SIFEN según la librería que uses
        // (ejemplo con roshka-sifen)
        return SifenConfig.builder()
            .ambiente(Ambiente.valueOf(ambiente))
            .idKiosco(idKiosco)
            .claveKiosco(claveKiosco)
            .certificado(certificado)
            .build();
    }
}
```

## 5. Diferencia con Servidor Filial

### Servidor Filial:
- Consulta SIFEN directamente
- Si falla el guardado en servidor central, retorna datos básicos
- Maneja casos donde el servidor central está offline

### Servidor Central:
- Consulta SIFEN directamente (o puede delegar al filial)
- Guarda directamente en su BD
- No necesita manejar "servidor central offline" (es el servidor central)

## 6. Flujo Completo

```
1. Usuario busca cliente por RUC/Documento
   ↓
2. Buscar en BD local
   ↓
3. ¿Cliente existe y está verificado?
   ├─ SÍ → Retornar cliente
   └─ NO → Continuar
   ↓
4. ¿SIFEN habilitado?
   ├─ NO → Retornar cliente existente o null
   └─ SÍ → Continuar
   ↓
5. Consultar SIFEN con RUC
   ↓
6. ¿Respuesta exitosa?
   ├─ NO → Log error, retornar cliente existente o null
   └─ SÍ → Continuar
   ↓
7. ¿Es contribuyente válido?
   ├─ NO → Lanzar excepción
   └─ SÍ → Continuar
   ↓
8. Crear/Actualizar Persona con datos de SIFEN
   ↓
9. Crear/Actualizar Cliente
   ↓
10. Guardar en BD
   ↓
11. Retornar Cliente
```

## 7. Consideraciones Importantes

1. **Normalización de RUC**: Siempre quitar guiones, puntos, espacios (solo números)
2. **Formato de nombre**: SIFEN puede retornar "APELLIDO, NOMBRE" → convertir a "NOMBRE APELLIDO"
3. **Validación de estado**: Verificar que el contribuyente esté ACTIVO
4. **Manejo de errores**: No fallar completamente si SIFEN no responde
5. **Caché**: Considerar cachear consultas recientes para evitar llamadas repetidas
6. **Logging**: Registrar todas las consultas para auditoría
7. **Timeout**: Configurar timeout para evitar bloqueos largos

## 8. Ejemplo de Uso en REST Controller

```java
@RestController
@RequestMapping("/api/personas")
public class PersonaController {
    
    @PostMapping("/buscar-por-documento")
    public ResponseEntity<?> buscarPorDocumento(@RequestParam String documento) {
        try {
            Cliente cliente = clienteService.buscarOCrearClientePorDocumento(documento);
            if (cliente == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(cliente);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al buscar cliente", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error interno"));
        }
    }
}
```

## 9. Dependencias Necesarias

Asegúrate de tener en tu `pom.xml`:

```xml
<dependency>
    <groupId>com.roshka</groupId>
    <artifactId>roshka-sifen</artifactId>
    <version>X.X.X</version>
</dependency>
```

O la librería de SIFEN que estés usando en el servidor filial.

## 10. Testing

### Casos de Prueba Recomendados:

1. **Consulta exitosa**: RUC válido y activo
2. **RUC no encontrado**: RUC que no existe en SIFEN
3. **RUC inactivo**: RUC que existe pero está inactivo
4. **Error de conexión**: SIFEN no disponible
5. **SIFEN deshabilitado**: Cuando `sifen.enabled=false`
6. **RUC con formato incorrecto**: Con guiones, puntos, etc.
7. **Cliente ya existe**: Verificar que actualice correctamente
8. **Cliente nuevo**: Verificar que cree correctamente

### Ejemplo de Test:

```java
@Test
public void testConsultaRucExitoso() {
    // Given
    String ruc = "1234567";
    
    // When
    ConsultaRucResponse respuesta = sifenService.consultaRuc(ruc);
    
    // Then
    assertNotNull(respuesta);
    assertTrue(respuesta.getProcesamientoCorrecto());
    assertEquals(ruc, respuesta.getRuc());
    assertNotNull(respuesta.getRazonSocial());
}

@Test
public void testConsultaRucNoEncontrado() {
    // Given
    String ruc = "999999999";
    
    // When
    ConsultaRucResponse respuesta = sifenService.consultaRuc(ruc);
    
    // Then
    assertNull(respuesta); // O verificar que procesamientoCorrecto = false
}
```

## 11. Troubleshooting

### Problema: SIFEN retorna null siempre
- **Solución**: Verificar configuración (certificado, credenciales, ambiente)
- Verificar logs de SIFEN para ver el error específico

### Problema: Timeout en consultas
- **Solución**: Configurar timeout en la configuración de SIFEN
- Considerar implementar retry con backoff exponencial

### Problema: Certificado inválido
- **Solución**: Verificar que el certificado esté vigente
- Verificar la ruta del certificado en application.properties

### Problema: Datos no se actualizan
- **Solución**: Verificar que `verificadoSet` se esté actualizando
- Verificar logs para ver si la consulta a SIFEN está funcionando

## 12. Mejoras Futuras

1. **Caché de consultas**: Implementar caché para evitar consultas repetidas
2. **Retry automático**: Implementar retry para errores transitorios
3. **Métricas**: Agregar métricas de consultas exitosas/fallidas
4. **Notificaciones**: Notificar cuando un cliente cambia de estado en SIFEN
5. **Sincronización periódica**: Actualizar clientes existentes periódicamente

