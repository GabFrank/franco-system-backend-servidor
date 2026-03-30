package com.franco.dev.service.sifen.util;

/**
 * Contiene los códigos geográficos oficiales de Paraguay para uso en SIFEN.
 * Estos códigos son requeridos en la generación de documentos electrónicos.
 */
public class CodigosGeograficos {

    /**
     * Códigos de departamentos de Paraguay según SIFEN
     */
    public enum Departamento {
        CAPITAL(1, "Capital"),
        CONCEPCION(2, "Concepción"),
        SAN_PEDRO(3, "San Pedro"),
        CORDILLERA(4, "Cordillera"),
        GUAIRA(5, "Guairá"),
        CAAGUAZU(6, "Caaguazú"),
        CAAZAPA(7, "Caazapá"),
        ITAPUA(8, "Itapúa"),
        MISIONES(9, "Misiones"),
        PARAGUARI(10, "Paraguarí"),
        ALTO_PARANA(11, "Alto Paraná"),
        CENTRAL(12, "Central"),
        NEEMBUCU(13, "Ñeembucú"),
        AMAMBAY(14, "Amambay"),
        CANINDEYU(15, "Canindeyú"),
        PRESIDENTE_HAYES(16, "Presidente Hayes"),
        ALTO_PARAGUAY(17, "Alto Paraguay"),
        BOQUERON(18, "Boquerón");

        private final int codigo;
        private final String nombre;

        Departamento(int codigo, String nombre) {
            this.codigo = codigo;
            this.nombre = nombre;
        }

        public int getCodigo() {
            return codigo;
        }

        public String getNombre() {
            return nombre;
        }

        /**
         * Busca un departamento por su código
         */
        public static Departamento porCodigo(int codigo) {
            for (Departamento dept : values()) {
                if (dept.codigo == codigo) {
                    return dept;
                }
            }
            return null;
        }

        /**
         * Busca un departamento por su nombre (insensible a mayúsculas y acentos)
         */
        public static Departamento porNombre(String nombre) {
            if (nombre == null) return null;
            String nombreNormalizado = normalizarTexto(nombre);
            for (Departamento dept : values()) {
                if (normalizarTexto(dept.nombre).equals(nombreNormalizado)) {
                    return dept;
                }
            }
            return null;
        }

        private static String normalizarTexto(String texto) {
            return texto.toLowerCase()
                    .replace("á", "a")
                    .replace("é", "e")
                    .replace("í", "i")
                    .replace("ó", "o")
                    .replace("ú", "u")
                    .replace("ñ", "n")
                    .trim();
        }
    }

    /**
     * Códigos de distritos/ciudades principales de Paraguay según SIFEN
     * Esta es una lista simplificada con los distritos más importantes.
     * Se puede extender según necesidad.
     */
    public enum Distrito {
        // Capital
        ASUNCION(1, "Asunción", Departamento.CAPITAL),
        
        // Concepción
        CONCEPCION_MUNICIPIO(2, "Concepción", Departamento.CONCEPCION),
        
        // Central - Distritos principales
        AREGUA(15, "Areguá", Departamento.CENTRAL),
        CAPIATA(16, "Capiatá", Departamento.CENTRAL),
        FERNANDO_DE_LA_MORA(17, "Fernando de la Mora", Departamento.CENTRAL),
        GUARAMBARE(18, "Guarambaré", Departamento.CENTRAL),
        ITA(19, "Itá", Departamento.CENTRAL),
        ITAUGUA(20, "Itauguá", Departamento.CENTRAL),
        LAMBARE(21, "Lambaré", Departamento.CENTRAL),
        LIMPIO(22, "Limpio", Departamento.CENTRAL),
        LUQUE(23, "Luque", Departamento.CENTRAL),
        MARIANO_ROQUE_ALONSO(24, "Mariano Roque Alonso", Departamento.CENTRAL),
        NUEVA_ITALIA(25, "Nueva Italia", Departamento.CENTRAL),
        NEMBY(26, "Ñemby", Departamento.CENTRAL),
        SAN_ANTONIO(27, "San Antonio", Departamento.CENTRAL),
        SAN_LORENZO(28, "San Lorenzo", Departamento.CENTRAL),
        VILLA_ELISA(29, "Villa Elisa", Departamento.CENTRAL),
        VILLETA(30, "Villeta", Departamento.CENTRAL),
        YPACARAI(31, "Ypacaraí", Departamento.CENTRAL),
        YPANE(32, "Ypané", Departamento.CENTRAL),
        
        // Alto Paraná
        CIUDAD_DEL_ESTE(116, "Ciudad del Este", Departamento.ALTO_PARANA),
        PRESIDENTE_FRANCO(117, "Presidente Franco", Departamento.ALTO_PARANA),
        HERNANDARIAS(118, "Hernandarias", Departamento.ALTO_PARANA),
        
        // Itapúa
        ENCARNACION(82, "Encarnación", Departamento.ITAPUA),
        
        // Otros distritos importantes se pueden agregar aquí según necesidad
        ;

        private final int codigo;
        private final String nombre;
        private final Departamento departamento;

        Distrito(int codigo, String nombre, Departamento departamento) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.departamento = departamento;
        }

        public int getCodigo() {
            return codigo;
        }

        public String getNombre() {
            return nombre;
        }

        public Departamento getDepartamento() {
            return departamento;
        }

        /**
         * Busca un distrito por su código
         */
        public static Distrito porCodigo(int codigo) {
            for (Distrito dist : values()) {
                if (dist.codigo == codigo) {
                    return dist;
                }
            }
            return null;
        }

        /**
         * Busca un distrito por su nombre (insensible a mayúsculas y acentos)
         */
        public static Distrito porNombre(String nombre) {
            if (nombre == null) return null;
            String nombreNormalizado = normalizarTexto(nombre);
            for (Distrito dist : values()) {
                if (normalizarTexto(dist.nombre).equals(nombreNormalizado)) {
                    return dist;
                }
            }
            return null;
        }

        private static String normalizarTexto(String texto) {
            return texto.toLowerCase()
                    .replace("á", "a")
                    .replace("é", "e")
                    .replace("í", "i")
                    .replace("ó", "o")
                    .replace("ú", "u")
                    .replace("ñ", "n")
                    .trim();
        }
    }
}

