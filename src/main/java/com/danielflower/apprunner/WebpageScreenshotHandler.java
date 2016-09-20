package com.danielflower.apprunner;

import org.apache.commons.exec.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class WebpageScreenshotHandler extends AbstractHandler {
    private static final Logger log = LoggerFactory.getLogger(WebpageScreenshotHandler.class);
    private final File dataDir;
    private final String template;
    private File phantomjsBin;

    public WebpageScreenshotHandler(File dataDir, File phantomjsBin) throws IOException {
        this.dataDir = dataDir;
        this.template = IOUtils.toString(
            WebpageScreenshotHandler.class.getResource("/phantom-template.js"));
        this.phantomjsBin = phantomjsBin;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String url = request.getParameter("url");
        if (!target.startsWith("/screenshots") || StringUtils.isEmpty(url)) {
            return;
        }

        File png = new File(dataDir, url.replaceAll("\\W+", "") + ".png");
        boolean refreshRequested = "true".equals(request.getParameter("refresh"));
        if (png.exists() && refreshRequested) {
            boolean deleted = FileUtils.deleteQuietly(png);
            log.info("Requested to delete screenshot. Deleted? " + deleted);
        }

        if (!png.isFile()) {
            synchronized (this) {
                if (!png.isFile()) {
                    log.info("Going to generate screenshot for " + png.getName());
                    File scriptFile = new File(dataDir, "phantomscript-" + UUID.randomUUID() + ".js");
                    String scriptPath = scriptFile.getCanonicalPath();
                    FileUtils.write(scriptFile,
                        template
                            .replace("{{url-to-screenshot}}", url)
                            .replace("{{output-path}}", png.getCanonicalPath().replace("\\", "\\\\"))
                    );
                    CommandLine command = new CommandLine(phantomjsBin)
                        .addArgument("--ignore-ssl-errors=yes") // to allow untrusted certs
                        .addArgument(scriptPath);
                    try {
                        run(command, dataDir, SECONDS.toMillis(45));
                    } catch (Exception e) {
                        log.warn("Error while creating screenshot", e);
                        response.sendError(500, "Error creating screenshot: " + e.getMessage());
                        baseRequest.setHandled(true);
                        return;
                    } finally {
                        FileUtils.deleteQuietly(scriptFile);
                    }
                }
            }
        }

        if (png.isFile()) {
            try (InputStream reader = new BufferedInputStream(new FileInputStream(png));
                 ServletOutputStream responseStream = response.getOutputStream()) {
                response.setContentType("image/png");
                String cacheControl = refreshRequested ? "no-store" : "public, max-age=" + HOURS.toSeconds(8);
                response.setHeader("Cache-Control", cacheControl);
                IOUtils.copy(reader, responseStream);
            }
        } else {
            response.sendError(404, "Not found");
        }
        baseRequest.setHandled(true);

    }

    private static Executor createExecutor(File projectRoot, ExecuteWatchdog watchDog) {
        Executor executor = new DefaultExecutor();
        executor.setWorkingDirectory(projectRoot);
        executor.setWatchdog(watchDog);
        executor.setStreamHandler(new PumpStreamHandler(new LogOutputStream() {
            @Override
            protected void processLine(String line, int logLevel) {
                log.info(line);
            }
        }));
        return executor;
    }

    private static void run(CommandLine command, File workingDir, long timeout) throws IOException {
        long startTime = logStartInfo(command);
        ExecuteWatchdog watchDog = new ExecuteWatchdog(timeout);
        Executor executor = createExecutor(workingDir, watchDog);
        int exitValue = executor.execute(command);
        if (executor.isFailure(exitValue)) {
            String message = watchDog.killedProcess()
                ? "Timed out waiting for " + command
                : "Exit code " + exitValue + " returned from " + command;
            throw new RuntimeException(message);
        }
        logEndTime(command, startTime);
    }

    private static long logStartInfo(CommandLine command) {
        log.info("Starting " + StringUtils.join(command.toStrings(), " "));
        return System.currentTimeMillis();
    }

    private static void logEndTime(CommandLine command, long startTime) {
        log.info("Completed " + command.getExecutable() + " in " + (System.currentTimeMillis() - startTime) + "ms");
    }

}
