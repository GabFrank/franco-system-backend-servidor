package com.franco.dev.service.configuraciones;

import com.franco.dev.domain.configuraciones.ModificacionDetalle;
import com.franco.dev.domain.configuraciones.ModificacionRegistro;
import com.franco.dev.domain.configuraciones.enums.TipoOperacion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.configuraciones.ModificacionDetalleRepository;
import com.franco.dev.repository.configuraciones.ModificacionRegistroRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ModificacionService extends CrudService<ModificacionRegistro, ModificacionRegistroRepository, Long> {

    @Autowired
    private final ModificacionRegistroRepository repository;

    @Autowired
    private final ModificacionDetalleRepository detalleRepository;

    @Autowired
    private final UsuarioService usuarioService;

    @Autowired
    private final SucursalService sucursalService;

    @Autowired
    private Environment env;

    @Override
    public ModificacionRegistroRepository getRepository() {
        return repository;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ModificacionRegistro registrarInsercion(Object entidad, String tipoEntidad, String schemaNombre,
            String tablaNombre) {
        try {
            System.out.println("=== INICIANDO REGISTRO DE INSERCIÓN ===");
            System.out.println("Tipo entidad: " + tipoEntidad);
            System.out.println("Schema: " + schemaNombre);
            System.out.println("Tabla: " + tablaNombre);

            ModificacionRegistro registro = crearRegistroBase(entidad, tipoEntidad, schemaNombre, tablaNombre,
                    TipoOperacion.INSERT);
            System.out.println("Registro base creado. Entidad ID: " + registro.getEntidadId());

            List<ModificacionDetalle> detalles = obtenerDetallesInsercion(entidad);
            System.out.println("Detalles obtenidos: " + detalles.size());

            if (detalles.isEmpty()) {
                System.out.println("No hay detalles, creando detalle básico");
                ModificacionDetalle detalleBasico = new ModificacionDetalle();
                detalleBasico.setCampoNombre("ACCION");
                detalleBasico.setCampoTipo("String");
                detalleBasico.setValorNuevo("Entidad creada");
                detalleBasico.setOrden(1);
                detalleBasico.setEsCampoSensible(false);
                detalles.add(detalleBasico);
            }

            ModificacionRegistro saved = repository.save(registro);
            repository.flush();
            System.out.println("Registro guardado con ID: " + saved.getId());

            for (ModificacionDetalle detalle : detalles) {
                detalle.setModificacionRegistro(saved);
                detalleRepository.save(detalle);
            }
            detalleRepository.flush();
            System.out.println("Detalles guardados: " + detalles.size());
            System.out.println("=== FIN REGISTRO DE INSERCIÓN ===");

            return saved;
        } catch (Exception e) {
            System.err.println("Error registrando inserción: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, noRollbackFor = Exception.class)
    public ModificacionRegistro registrarActualizacion(Object entidadAnterior, Object entidadNueva, String tipoEntidad,
            String schemaNombre, String tablaNombre) {
        try {
            if (entidadAnterior == null || entidadNueva == null) {
                return null;
            }

            ModificacionRegistro registro = crearRegistroBase(entidadNueva, tipoEntidad, schemaNombre, tablaNombre,
                    TipoOperacion.UPDATE);

            List<ModificacionDetalle> detalles = obtenerDetallesActualizacion(entidadAnterior, entidadNueva);
            if (detalles.isEmpty()) {
                ModificacionDetalle detalleBasico = new ModificacionDetalle();
                detalleBasico.setCampoNombre("ACTUALIZACION");
                detalleBasico.setCampoTipo("String");
                detalleBasico.setValorAnterior("Entidad actualizada");
                detalleBasico.setValorNuevo("Entidad actualizada");
                detalleBasico.setOrden(1);
                detalleBasico.setEsCampoSensible(false);
                detalles.add(detalleBasico);
            }

            ModificacionRegistro saved = repository.save(registro);
            repository.flush();

            for (ModificacionDetalle detalle : detalles) {
                detalle.setModificacionRegistro(saved);
                detalleRepository.save(detalle);
            }
            detalleRepository.flush();

            return saved;
        } catch (Throwable e) {
            System.err.println("Error registrando actualización de " + tipoEntidad + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Causa: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            return null;
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ModificacionRegistro registrarEliminacion(Object entidad, String tipoEntidad, String schemaNombre,
            String tablaNombre) {
        try {
            if (entidad == null) {
                return null;
            }

            ModificacionRegistro registro = crearRegistroBase(entidad, tipoEntidad, schemaNombre, tablaNombre,
                    TipoOperacion.DELETE);

            List<ModificacionDetalle> detalles = obtenerDetallesEliminacion(entidad);

            if (detalles.isEmpty()) {
                return null;
            }

            ModificacionRegistro saved = repository.save(registro);
            repository.flush();

            for (ModificacionDetalle detalle : detalles) {
                detalle.setModificacionRegistro(saved);
                detalleRepository.save(detalle);
            }
            detalleRepository.flush();

            return saved;
        } catch (Exception e) {
            System.err.println("Error registrando eliminación: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private ModificacionRegistro crearRegistroBase(Object entidad, String tipoEntidad, String schemaNombre,
            String tablaNombre, TipoOperacion tipoOperacion) {
        ModificacionRegistro registro = new ModificacionRegistro();
        registro.setTipoEntidad(tipoEntidad);
        registro.setSchemaNombre(schemaNombre);
        registro.setTablaNombre(tablaNombre);
        registro.setTipoOperacion(tipoOperacion);
        registro.setModificadoEn(LocalDateTime.now());
        registro.setCreadoEn(LocalDateTime.now());
        registro.setActivo(true);

        try {
            Field idField = entidad.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object idValue = idField.get(entidad);
            if (idValue != null) {
                Long entidadId = null;
                if (idValue instanceof Number) {
                    entidadId = ((Number) idValue).longValue();
                } else if (idValue instanceof Long) {
                    entidadId = (Long) idValue;
                }
                if (entidadId != null && entidadId > 0) {
                    registro.setEntidadId(entidadId);
                    System.out.println("ID de entidad obtenido en crearRegistroBase: " + entidadId);
                } else {
                    System.out.println("ID de entidad es null o 0 en crearRegistroBase: " + idValue);
                }
            } else {
                System.out.println("No se pudo obtener ID de la entidad (idValue es null) en crearRegistroBase");
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo ID de entidad en crearRegistroBase: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String username = authentication.getName();
                if (username != null) {
                    Optional<Usuario> usuarioOpt = usuarioService.findByNickname(username);
                    if (usuarioOpt.isPresent()) {
                        registro.setUsuario(usuarioOpt.get());
                    }
                }
            }
        } catch (Exception e) {
        }

        try {
            if (env != null) {
                String sucursalIdStr = env.getProperty("sucursalId");
                if (sucursalIdStr != null) {
                    Long sucursalId = Long.valueOf(sucursalIdStr);
                    Optional<Sucursal> sucursalOpt = sucursalService.findById(sucursalId);
                    if (sucursalOpt.isPresent()) {
                        registro.setSucursal(sucursalOpt.get());
                    }
                }
            }
        } catch (Exception e) {
        }

        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                if (request != null) {
                    String ipAddress = getClientIpAddress(request);
                    registro.setIpAddress(ipAddress);
                    registro.setUserAgent(request.getHeader("User-Agent"));
                }
            }
        } catch (Exception e) {
        }

        return registro;
    }

    private List<ModificacionDetalle> obtenerDetallesInsercion(Object entidad) {
        List<ModificacionDetalle> detalles = new ArrayList<>();
        Field[] fields = entidad.getClass().getDeclaredFields();
        int orden = 1;

        System.out.println("Obteniendo detalles de inserción para: " + entidad.getClass().getSimpleName());
        System.out.println("Total de campos: " + fields.length);

        for (Field field : fields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                    field.getName().equals("serialVersionUID") ||
                    field.isAnnotationPresent(javax.persistence.OneToMany.class) ||
                    field.isAnnotationPresent(javax.persistence.ManyToMany.class)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(entidad);

                ModificacionDetalle detalle = new ModificacionDetalle();
                detalle.setCampoNombre(field.getName());
                detalle.setCampoTipo(field.getType().getSimpleName());
                detalle.setOrden(orden++);
                detalle.setEsCampoSensible(esCampoSensible(field.getName()));

                if (field.isAnnotationPresent(javax.persistence.ManyToOne.class)) {
                    if (value != null) {
                        try {
                            Field idField = value.getClass().getDeclaredField("id");
                            idField.setAccessible(true);
                            Object idValue = idField.get(value);
                            if (idValue != null) {
                                detalle.setValorNuevoId(((Number) idValue).longValue());
                                detalle.setValorNuevo(value.getClass().getSimpleName() + " (ID: " + idValue + ")");
                            } else {
                                detalle.setValorNuevo(value.getClass().getSimpleName() + " (sin ID)");
                            }
                        } catch (Exception e) {
                            detalle.setValorNuevo(value != null ? value.toString() : null);
                        }
                    } else {
                        detalle.setValorNuevo(null);
                    }
                } else {
                    detalle.setValorNuevo(value != null ? value.toString() : null);
                }

                detalles.add(detalle);
                System.out.println("Detalle agregado: " + field.getName() + " = " + detalle.getValorNuevo());
            } catch (Exception e) {
                System.err.println("Error procesando campo " + field.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Total detalles obtenidos: " + detalles.size());
        return detalles;
    }

    private List<ModificacionDetalle> obtenerDetallesActualizacion(Object entidadAnterior, Object entidadNueva) {
        List<ModificacionDetalle> detalles = new ArrayList<>();
        Field[] fields = entidadNueva.getClass().getDeclaredFields();
        int orden = 1;

        for (Field field : fields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                    field.getName().equals("serialVersionUID") ||
                    field.isAnnotationPresent(javax.persistence.OneToMany.class) ||
                    field.isAnnotationPresent(javax.persistence.OneToOne.class) ||
                    field.isAnnotationPresent(javax.persistence.ManyToMany.class)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object valorAnterior = entidadAnterior != null ? field.get(entidadAnterior) : null;
                Object valorNuevo = field.get(entidadNueva);

                boolean valoresSonIguales = sonIguales(valorAnterior, valorNuevo);

                if (!valoresSonIguales) {
                    ModificacionDetalle detalle = new ModificacionDetalle();
                    detalle.setCampoNombre(field.getName());
                    detalle.setCampoTipo(field.getType().getSimpleName());
                    detalle.setOrden(orden++);
                    detalle.setEsCampoSensible(esCampoSensible(field.getName()));

                    if (field.isAnnotationPresent(javax.persistence.ManyToOne.class)) {
                        if (valorAnterior != null) {
                            try {
                                Field idField = valorAnterior.getClass().getDeclaredField("id");
                                idField.setAccessible(true);
                                Object idValue = idField.get(valorAnterior);
                                if (idValue != null) {
                                    detalle.setValorAnteriorId(((Number) idValue).longValue());
                                    detalle.setValorAnterior(
                                            valorAnterior.getClass().getSimpleName() + " (ID: " + idValue + ")");
                                } else {
                                    detalle.setValorAnterior(valorAnterior.getClass().getSimpleName() + " (sin ID)");
                                }
                            } catch (Exception e) {
                                detalle.setValorAnterior(valorAnterior != null ? valorAnterior.toString() : null);
                            }
                        } else {
                            detalle.setValorAnterior(null);
                        }

                        if (valorNuevo != null) {
                            try {
                                Field idField = valorNuevo.getClass().getDeclaredField("id");
                                idField.setAccessible(true);
                                Object idValue = idField.get(valorNuevo);
                                if (idValue != null) {
                                    detalle.setValorNuevoId(((Number) idValue).longValue());
                                    detalle.setValorNuevo(
                                            valorNuevo.getClass().getSimpleName() + " (ID: " + idValue + ")");
                                } else {
                                    detalle.setValorNuevo(valorNuevo.getClass().getSimpleName() + " (sin ID)");
                                }
                            } catch (Exception e) {
                                detalle.setValorNuevo(valorNuevo != null ? valorNuevo.toString() : null);
                            }
                        } else {
                            detalle.setValorNuevo(null);
                        }
                    } else {
                        if (valorAnterior instanceof java.time.LocalDateTime) {
                            detalle.setValorAnterior(valorAnterior.toString());
                        } else if (valorAnterior instanceof java.time.LocalDate) {
                            detalle.setValorAnterior(valorAnterior.toString());
                        } else {
                            detalle.setValorAnterior(valorAnterior != null ? valorAnterior.toString() : null);
                        }

                        if (valorNuevo instanceof java.time.LocalDateTime) {
                            detalle.setValorNuevo(valorNuevo.toString());
                        } else if (valorNuevo instanceof java.time.LocalDate) {
                            detalle.setValorNuevo(valorNuevo.toString());
                        } else {
                            detalle.setValorNuevo(valorNuevo != null ? valorNuevo.toString() : null);
                        }
                    }

                    detalles.add(detalle);
                }
            } catch (Exception e) {
            }
        }

        return detalles;
    }

    private List<ModificacionDetalle> obtenerDetallesEliminacion(Object entidad) {
        List<ModificacionDetalle> detalles = new ArrayList<>();
        Field[] fields = entidad.getClass().getDeclaredFields();
        int orden = 1;

        for (Field field : fields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                    field.getName().equals("serialVersionUID") ||
                    field.isAnnotationPresent(javax.persistence.ManyToOne.class) ||
                    field.isAnnotationPresent(javax.persistence.OneToMany.class) ||
                    field.isAnnotationPresent(javax.persistence.OneToOne.class) ||
                    field.isAnnotationPresent(javax.persistence.ManyToMany.class)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(entidad);

                ModificacionDetalle detalle = new ModificacionDetalle();
                detalle.setCampoNombre(field.getName());
                detalle.setCampoTipo(field.getType().getSimpleName());
                detalle.setValorAnterior(value != null ? value.toString() : null);
                detalle.setOrden(orden++);
                detalle.setEsCampoSensible(esCampoSensible(field.getName()));

                detalles.add(detalle);
            } catch (Exception e) {
            }
        }

        return detalles;
    }

    private boolean sonIguales(Object valor1, Object valor2) {
        if (valor1 == null && valor2 == null) {
            return true;
        }
        if (valor1 == null || valor2 == null) {
            return false;
        }

        if (!valor1.getClass().equals(valor2.getClass())) {
            return false;
        }

        try {
            Field idField1 = valor1.getClass().getDeclaredField("id");
            Field idField2 = valor2.getClass().getDeclaredField("id");
            idField1.setAccessible(true);
            idField2.setAccessible(true);
            Object id1 = idField1.get(valor1);
            Object id2 = idField2.get(valor2);

            if (id1 != null && id2 != null) {
                return id1.equals(id2);
            }
            if (id1 != null || id2 != null) {
                return false;
            }
        } catch (NoSuchFieldException e) {
        } catch (Exception e) {
            return false;
        }

        if (valor1 instanceof Number && valor2 instanceof Number) {
            return ((Number) valor1).doubleValue() == ((Number) valor2).doubleValue();
        }

        if (valor1 instanceof Boolean && valor2 instanceof Boolean) {
            return valor1.equals(valor2);
        }

        if (valor1 instanceof java.time.LocalDateTime && valor2 instanceof java.time.LocalDateTime) {
            return valor1.equals(valor2);
        }

        if (valor1 instanceof java.time.LocalDate && valor2 instanceof java.time.LocalDate) {
            return valor1.equals(valor2);
        }

        try {
            return valor1.equals(valor2);
        } catch (Exception e) {
            try {
                return valor1.toString().equals(valor2.toString());
            } catch (Exception e2) {
                return false;
            }
        }
    }

    private boolean esCampoSensible(String nombreCampo) {
        String nombreLower = nombreCampo.toLowerCase();
        return nombreLower.contains("precio") ||
                nombreLower.contains("costo") ||
                nombreLower.contains("password") ||
                nombreLower.contains("token") ||
                nombreLower.contains("secret");
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
