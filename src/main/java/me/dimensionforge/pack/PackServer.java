package me.dimensionforge.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** Servidor HTTP mínimo (JDK) que sirve el pack propio y, opcionalmente, el zip de BetterModel. */
public final class PackServer {

    private final Logger log;
    private HttpServer server;
    private volatile byte[] pack = new byte[0];
    private volatile Path extra;

    public PackServer(Logger log) {
        this.log = log;
    }

    public void setPack(byte[] data) {
        this.pack = data;
    }

    /** Archivo adicional servido en /bettermodel.zip (null = ninguno). */
    public void setExtra(Path file) {
        this.extra = file;
    }

    public boolean running() {
        return server != null;
    }

    public void start(String bind, int port) throws IOException {
        stop();
        HttpServer s = HttpServer.create(new InetSocketAddress(bind, port), 0);
        s.createContext("/pack.zip", ex -> serve(ex, pack));
        s.createContext("/bettermodel.zip", ex -> {
            Path f = extra;
            if (f == null || !Files.isRegularFile(f)) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            serve(ex, Files.readAllBytes(f));
        });
        s.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "DimensionForge-PackServer");
            t.setDaemon(true);
            return t;
        }));
        s.start();
        server = s;
        log.info("Servidor del pack escuchando en " + bind + ":" + port);
    }

    private void serve(HttpExchange ex, byte[] data) throws IOException {
        try {
            if (data.length == 0) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        } finally {
            ex.close();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
