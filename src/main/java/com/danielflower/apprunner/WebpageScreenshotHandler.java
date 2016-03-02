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
import java.nio.channels.Channels;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.*;

public class WebpageScreenshotHandler extends AbstractHandler {
    public static final Logger log = LoggerFactory.getLogger(WebpageScreenshotHandler.class);
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
        if (!png.isFile()) {
            File scriptFile = new File(dataDir, "phantomscript-" + UUID.randomUUID() + ".js");
            String scriptPath = scriptFile.getCanonicalPath();
            log.info("Going to create " + scriptPath);
            FileUtils.write(scriptFile,
                template
                    .replace("{{url-to-screenshot}}", url)
                    .replace("{{output-path}}", png.getCanonicalPath().replace("\\", "\\\\"))
            );
            CommandLine command = new CommandLine(phantomjsBin).addArgument(scriptPath);
            run(command, dataDir, SECONDS.toMillis(45));
        }

        if (png.isFile()) {
            try (InputStream reader = new BufferedInputStream(new FileInputStream(png));
                 ServletOutputStream responseStream = response.getOutputStream()) {
                response.setContentType("image/png");
                response.setHeader("Cache-Control", "public, max-age=" + HOURS.toSeconds(8));
                IOUtils.copy(reader, responseStream);
            }
            baseRequest.setHandled(true);
        }

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

    public static void run(CommandLine command, File workingDir, long timeout) throws IOException {
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

    public static long logStartInfo(CommandLine command) {
        log.info("Starting " + StringUtils.join(command.toStrings(), " "));
        return System.currentTimeMillis();
    }

    private static void logEndTime(CommandLine command, long startTime) {
        log.info("Completed " + command.getExecutable() + " in " + (System.currentTimeMillis() - startTime) + "ms");
    }

}
