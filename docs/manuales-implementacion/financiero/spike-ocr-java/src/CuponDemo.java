import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Genera un cupon de POS sintetico, parecido a lo que imprime una maquinita termica. */
public class CuponDemo {
    public static void main(String[] args) throws Exception {
        int w = 720, h = 1000;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);

        String[] lineas = {
            "SUPERMERCADO DEMO S.A.",
            "SUC. CENTRAL - ASUNCION",
            "",
            "VENTA CON TARJETA",
            "",
            "FECHA: 12/09/2026  HORA: 14:35",
            "",
            "TERMINAL: JF798SJJ",
            "COMERCIO: 00451233",
            "",
            "AUT: 883921",
            "BOLETA: 00045",
            "",
            "MONTO: 150.000",
            "MONEDA: GS",
            "",
            "TARJETA: ************4821",
            "",
            "GRACIAS POR SU COMPRA"
        };

        Font grande = new Font("Courier New", Font.BOLD, 34);
        Font normal = new Font("Courier New", Font.PLAIN, 30);
        int y = 70;
        for (String l : lineas) {
            if (l.isEmpty()) { y += 22; continue; }
            g.setFont(l.startsWith("AUT") || l.startsWith("MONTO") || l.startsWith("TERMINAL") ? grande : normal);
            g.drawString(l, 60, y);
            y += 46;
        }
        g.dispose();
        File out = new File(args[0]);
        ImageIO.write(img, "jpg", out);
        System.out.println("escrito: " + out.getAbsolutePath() + " (" + out.length() + " bytes)");
    }
}
