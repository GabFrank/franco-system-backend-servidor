package com.franco.dev.eventos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Valida una suscripcion contra el catalogo antes de guardarla en el central (srv #587, C1). La filial
 * descarta lo desconocido solo como defensa; aca se rechaza con un error claro.
 */
public final class ValidadorSuscripcion {

    private ValidadorSuscripcion() {
    }

    /** Lo que importa del consumidor para validar. */
    public static final class Permisos {
        public final boolean personales;
        public final boolean sensibles;

        public Permisos(boolean personales, boolean sensibles) {
            this.personales = personales;
            this.sensibles = sensibles;
        }
    }

    /** Lista de errores (vacia si es valida). */
    public static List<String> validar(Suscripcion s, Permisos p, List<String> hostsPermitidos) {
        List<String> errores = new ArrayList<>();
        if (s.getUrl() == null || s.getUrl().trim().isEmpty()) {
            errores.add("Falta la URL");
        } else {
            String e = DestinoSeguro.validar(s.getUrl().trim(), hostsPermitidos);
            if (e != null) errores.add("URL: " + e);
        }
        if (s.getEventos() == null || s.getEventos().isEmpty()) errores.add("Elegi al menos un evento");
        if (s.getEventos() != null) {
            for (String patron : s.getEventos()) {
                if (patron == null || patron.trim().isEmpty()) continue;
                if (!existePatron(patron.trim())) errores.add("Evento desconocido: " + patron);
            }
        }
        if (s.getEntrega() != null && !s.getEntrega().trim().isEmpty()) {
            String en = s.getEntrega().trim().toLowerCase();
            if (!en.equals("individual") && !en.matches("lote(:[0-9]+)?")) {
                errores.add("Entrega invalida: usa individual, lote o lote:<segundos>");
            }
        }
        if (s.getFiltros() != null) {
            for (Map.Entry<String, Map<String, List<Object>>> f : s.getFiltros().entrySet()) {
                CatalogoEventos.Tipo t = CatalogoEventos.tipo(f.getKey());
                if (t == null) {
                    errores.add("Filtro para un evento desconocido: " + f.getKey());
                    continue;
                }
                if (f.getValue() == null) continue;
                for (String atributo : f.getValue().keySet()) {
                    if (!t.atributos.contains(atributo)) {
                        errores.add("El evento " + f.getKey() + " no tiene el atributo de filtro " + atributo
                                + " (tiene: " + String.join(", ", t.atributos) + ")");
                    }
                }
            }
        }
        if (s.getUmbrales() != null) {
            for (String tipo : s.getUmbrales().keySet()) {
                if (CatalogoEventos.tipo(tipo) == null) errores.add("Umbral para un evento desconocido: " + tipo);
            }
        }
        if (s.getCampos() != null) {
            for (Map.Entry<String, List<String>> c : s.getCampos().entrySet()) {
                CatalogoEventos.Tipo t = CatalogoEventos.tipo(c.getKey());
                if (t == null) {
                    errores.add("Campos para un evento desconocido: " + c.getKey());
                    continue;
                }
                if (c.getValue() == null) continue;
                for (String ruta : c.getValue()) {
                    CatalogoEventos.Clase clase = claseDeRuta(t, ruta);
                    if (clase == null) {
                        errores.add("El evento " + c.getKey() + " no tiene el campo " + ruta);
                    } else if (clase == CatalogoEventos.Clase.PERSONAL && !s.isDatosPersonales()) {
                        errores.add("El campo " + ruta + " de " + c.getKey() + " es personal: la suscripcion necesita datos personales");
                    } else if (clase == CatalogoEventos.Clase.SENSIBLE && !s.isDatosSensibles()) {
                        errores.add("El campo " + ruta + " de " + c.getKey() + " es sensible: la suscripcion necesita datos sensibles");
                    }
                }
            }
        }
        if (s.isDatosPersonales() && (p == null || !p.personales)) {
            errores.add("El consumidor no tiene permiso para datos personales");
        }
        if (s.isDatosSensibles() && (p == null || !p.sensibles)) {
            errores.add("El consumidor no tiene permiso para datos sensibles");
        }
        return errores;
    }

    /** Un tipo exacto del catalogo, nodo.latido, o un comodin ("caja.*") que cubra al menos uno. */
    static boolean existePatron(String patron) {
        if ("nodo.latido".equals(patron) || "*".equals(patron)) return true;
        if (CatalogoEventos.tipo(patron) != null) return true;
        if (patron.endsWith(".*")) {
            for (String t : CatalogoEventos.todos().keySet()) {
                if (ReglasEntrega.coincideTipo(patron, t)) return true;
            }
        }
        return false;
    }

    /**
     * La clase de una ruta exacta; un prefijo de rutas ("caja" cubre "caja.id") vale como operativo, porque
     * la lista blanca igual recorta lo personal o sensible que no tenga permiso. null si no existe.
     */
    static CatalogoEventos.Clase claseDeRuta(CatalogoEventos.Tipo t, String ruta) {
        if (ruta == null) return null;
        CatalogoEventos.Clase c = t.rutas.get(ruta);
        if (c != null) return c;
        for (String r : t.rutas.keySet()) {
            if (r.startsWith(ruta + ".")) return CatalogoEventos.Clase.OPERATIVO;
        }
        return null;
    }
}
