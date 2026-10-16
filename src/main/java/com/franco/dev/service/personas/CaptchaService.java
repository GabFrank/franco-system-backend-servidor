package com.franco.dev.service.personas;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captcha de imagen generado aca mismo, para los endpoints publicos de recuperacion de contrasena.
 *
 * <p><b>Por que propio y no reCAPTCHA/Turnstile.</b> Los dos exigen registrar el dominio del
 * sitio, y el desktop corre en Electron sin un dominio fijo; ademas dependen de internet en cada
 * sucursal y mandan datos a terceros.
 *
 * <p>Vive en memoria: un captcha dura {@link #VIGENCIA_SEGUNDOS} y sirve para un solo intento,
 * acierte o no. Si el central se reinicia, el usuario pide otro. El mapa tiene tope para que nadie
 * pueda llenar la memoria pidiendo captchas.
 */
@Service
public class CaptchaService {

    static final int VIGENCIA_SEGUNDOS = 120;
    private static final int MAXIMO_VIGENTES = 5000;
    private static final int LARGO = 5;
    /** Sin caracteres que se confunden entre si (0/O, 1/I/L). */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int ANCHO = 200;
    private static final int ALTO = 64;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pendiente> vigentes = new ConcurrentHashMap<>();

    public static class Captcha {
        private final String id;
        private final String imagen;

        Captcha(String id, String imagen) {
            this.id = id;
            this.imagen = imagen;
        }

        public String getId() { return id; }

        /** PNG en base64, listo para un {@code data:image/png;base64,}. */
        public String getImagen() { return imagen; }
    }

    private static class Pendiente {
        final String respuesta;
        final Instant vence;

        Pendiente(String respuesta, Instant vence) {
            this.respuesta = respuesta;
            this.vence = vence;
        }
    }

    public Captcha generar() {
        limpiarVencidos();
        if (vigentes.size() >= MAXIMO_VIGENTES) {
            throw new IllegalStateException("Demasiados pedidos en curso. Intente en unos minutos.");
        }
        StringBuilder texto = new StringBuilder(LARGO);
        for (int i = 0; i < LARGO; i++) {
            texto.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        String id = UUID.randomUUID().toString();
        vigentes.put(id, new Pendiente(texto.toString(), Instant.now().plusSeconds(VIGENCIA_SEGUNDOS)));
        return new Captcha(id, dibujar(texto.toString()));
    }

    /** Consume el captcha: despues de esta llamada ese id ya no sirve, haya acertado o no. */
    public boolean validar(String id, String respuesta) {
        if (id == null || respuesta == null) return false;
        Pendiente p = vigentes.remove(id);
        if (p == null || Instant.now().isAfter(p.vence)) return false;
        return p.respuesta.equalsIgnoreCase(respuesta.replaceAll("\\s", ""));
    }

    private void limpiarVencidos() {
        Instant ahora = Instant.now();
        Iterator<Map.Entry<String, Pendiente>> it = vigentes.entrySet().iterator();
        while (it.hasNext()) {
            if (ahora.isAfter(it.next().getValue().vence)) it.remove();
        }
    }

    private String dibujar(String texto) {
        BufferedImage img = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(236, 236, 236));
            g.fillRect(0, 0, ANCHO, ALTO);

            for (int i = 0; i < 120; i++) {
                g.setColor(colorAleatorio(150, 220));
                g.fillOval(random.nextInt(ANCHO), random.nextInt(ALTO), 2, 2);
            }

            // SansSerif es una fuente logica: la JVM siempre la tiene, no depende del servidor.
            Font base = new Font(Font.SANS_SERIF, Font.BOLD, 34);
            int x = 16;
            for (char c : texto.toCharArray()) {
                AffineTransform t = new AffineTransform();
                t.rotate(Math.toRadians(random.nextInt(50) - 25));
                g.setFont(base.deriveFont(t));
                g.setColor(colorAleatorio(20, 110));
                g.drawString(String.valueOf(c), x, 42 + random.nextInt(10) - 5);
                x += 34 + random.nextInt(4);
            }

            g.setStroke(new BasicStroke(2f));
            for (int i = 0; i < 5; i++) {
                g.setColor(colorAleatorio(60, 160));
                g.drawLine(random.nextInt(ANCHO), random.nextInt(ALTO),
                        random.nextInt(ANCHO), random.nextInt(ALTO));
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el captcha", e);
        }
    }

    private Color colorAleatorio(int min, int max) {
        int rango = max - min;
        return new Color(min + random.nextInt(rango), min + random.nextInt(rango), min + random.nextInt(rango));
    }
}
