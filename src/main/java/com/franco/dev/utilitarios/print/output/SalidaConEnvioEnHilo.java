package com.franco.dev.utilitarios.print.output;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pipe hacia un hilo que manda los datos a la impresora (CUPS o socket).
 * <p>
 * Si ese hilo termina sin haber leido, el que escribe esperaria lugar en el pipe para siempre y el
 * request no terminaria (issue #205). Por eso el hilo, al terminar, siempre cierra su punta: el
 * {@code write} pendiente sale con {@link IOException}, y si el envio fallo, con el motivo.
 * <p>
 * Un ticket chico entra entero en el buffer del pipe y ningun {@code write} se entera de la falla:
 * por eso {@link #close()} espera al hilo de envio y avisa si fallo (issue #534).
 */
abstract class SalidaConEnvioEnHilo extends PipedOutputStream {

    protected final PipedInputStream pipedInputStream;

    /** Cuanto espera {@link #close()} al hilo de envio. CUPS tarda menos de 1 s en tomar un trabajo. */
    long esperaEnvioMillis = 10_000;

    /** Por que fallo el envio, si fallo. Lo escribe el hilo de envio. */
    private volatile Throwable fallaEnvio;
    /** Llega a cero cuando el hilo de envio termino y cerro su punta del pipe. */
    private final CountDownLatch envioTerminado = new CountDownLatch(1);
    private final AtomicBoolean cerrado = new AtomicBoolean();

    protected SalidaConEnvioEnHilo() throws IOException {
        pipedInputStream = new PipedInputStream();
        super.connect(pipedInputStream);
    }

    /** Lo llama el hilo de envio cuando el envio falla, antes de terminar. */
    protected void registrarFalla(Throwable causa) {
        fallaEnvio = causa;
    }

    /** Lo llama el hilo de envio al terminar, siempre (en un finally). */
    protected void terminarEnvio() {
        envioTerminado.countDown();
        try {
            pipedInputStream.close();
        } catch (IOException ignorada) {
            // cerrar un PipedInputStream no falla
        }
    }

    @Override
    public void write(int b) throws IOException {
        try {
            super.write(b);
        } catch (IOException e) {
            throw conMotivo(e);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            super.write(b, off, len);
        } catch (IOException e) {
            throw conMotivo(e);
        }
    }

    /**
     * Cierra el pipe y espera a que el hilo de envio termine. Solo el primer cierre espera y avisa:
     * los llamadores suelen cerrar dos veces ({@code escpos.close()} y despues la salida).
     *
     * @exception IOException si el envio fallo o no termino a tiempo.
     */
    @Override
    public void close() throws IOException {
        super.close();
        if (!cerrado.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!envioTerminado.await(esperaEnvioMillis, TimeUnit.MILLISECONDS)) {
                // El hilo sigue: el ticket puede salir igual, de ahi el aviso antes de reintentar.
                throw new IOException("No se pudo confirmar la impresion en " + esperaEnvioMillis / 1000
                        + " s: revisar la impresora antes de reintentar");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Se interrumpio la espera de la impresion");
        }
        Throwable causa = fallaEnvio;
        if (causa != null) {
            throw new IOException("No se pudo imprimir: " + causa.getMessage(), causa);
        }
    }

    /** El error del pipe con el motivo real, si el envio fallo o termino antes de tiempo. */
    private IOException conMotivo(IOException e) {
        Throwable causa = fallaEnvio;
        if (causa != null) {
            return new IOException("No se pudo imprimir: " + causa.getMessage(), causa);
        }
        if (envioTerminado.getCount() == 0) {
            return new IOException("No se pudo imprimir: la impresora no tomo el trabajo", e);
        }
        return e;
    }
}
