package io.github.dgviz.ui;

import io.github.dgviz.export.HtmlReportExporter;
import io.github.dgviz.model.AnalysisResult;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Embedded Jetty 12 server that serves the interactive dependency graph.
 */
public final class VisualizationServer {

    private static final Logger log = LoggerFactory.getLogger(VisualizationServer.class);

    private final int port;
    private final AnalysisResult result;

    public VisualizationServer(int port, AnalysisResult result) {
        this.port = port;
        this.result = result;
    }

    public void startBlocking() throws Exception {
        Path tempHtml = Files.createTempFile("dgviz-ui-", ".html");
        new HtmlReportExporter().export(result, tempHtml);
        byte[] content = Files.readAllBytes(tempHtml);

        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setContentType("text/html; charset=utf-8");
                try (OutputStream out = resp.getOutputStream()) {
                    out.write(content);
                }
            }
        }), "/*");
        server.setHandler(context);
        server.start();
        log.info("Visualization UI available at http://localhost:{}", port);
        System.out.println("Open http://localhost:" + port + " (Ctrl+C to stop)");
        server.join();
    }
}
