package com.danielflower.apprunner;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

public class App {
    public static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        try {
            String env = firstNonNull(System.getenv("APP_ENV"), "local"); // "prod" or "local"
            boolean isLocal = "local".equals(env);
            File tempDir = new File(firstNonNull(System.getenv("TEMP"), "target/data"));

            // When run from app-runner, you must use the port set in the environment variable APP_PORT
            int port = Integer.parseInt(firstNonNull(System.getenv("APP_PORT"), "8081"));
            // All URLs must be prefixed with the app name, which is got via the APP_NAME env var.
            String appName = firstNonNull(System.getenv("APP_NAME"), "app-runner-home");


            start(isLocal, port, appName, tempDir);
        } catch (Exception e) {
            log.error("Error on startup", e);
            System.exit(1);
        }
    }

    private static void start(boolean isLocal, int port, String appName, File tempDir) throws Exception {

        Server jettyServer = new Server(new InetSocketAddress("localhost", port));
        jettyServer.setStopAtShutdown(true);

        HandlerList handlers = new HandlerList();

        Optional<String> appRunnerRestUrlBase = isLocal ? Optional.of("http://localhost:8080") : Optional.empty();
        HttpClient client = new HttpClient(new SslContextFactory(true));
        client.start();

        TemplateEngine engine = createTemplateEngine(isLocal);

        handlers.addHandler(new HomeController(client, engine, appRunnerRestUrlBase));

        addScreenshotHandlerIfPhantomJSIsAvailable(tempDir, handlers);
        handlers.addHandler(resourceHandler(isLocal));

        // you must serve everything from a directory named after your app
        ContextHandler ch = new ContextHandler();
        ch.setContextPath("/" + appName);
        ch.setHandler(handlers);
        jettyServer.setHandler(ch);

        try {
            jettyServer.start();
        } catch (Throwable e) {
            log.error("Error on start", e);
            System.exit(1);
        }

        log.info("Started " + appName + " at http://localhost:" + port + ch.getContextPath());

        jettyServer.join();
    }

    private static void addScreenshotHandlerIfPhantomJSIsAvailable(File dataDir, HandlerList handlers) throws IOException {
        String phantomjsBinPath = System.getenv("PHANTOMJS_BIN");
        if (phantomjsBinPath == null) {
            log.warn("No PHANTOMJS_BIN env var set, so no screenshots are available");
        } else {
            File phantomjsBin = new File(phantomjsBinPath);
            if (!phantomjsBin.isFile()) {
                log.warn("Could not find " + phantomjsBin.getCanonicalPath() + " so no screenshots are available");
            } else {
                handlers.addHandler(new WebpageScreenshotHandler(dataDir, phantomjsBin));
            }
        }
    }

    private static TemplateEngine createTemplateEngine(boolean isLocal) {
        TemplateEngine engine = new TemplateEngine();
        AbstractConfigurableTemplateResolver resolver;
        if (isLocal) {
            resolver = new FileTemplateResolver();
            resolver.setPrefix("src/main/resources/views/");
            resolver.setCacheable(false);
        } else {
            resolver = new ClassLoaderTemplateResolver(App.class.getClassLoader());
            resolver.setPrefix("/views/");
            resolver.setCacheable(true);
        }
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static Handler resourceHandler(boolean useFileSystem) {
        ResourceHandler resourceHandler = new ResourceHandler();
        if (useFileSystem) {
            resourceHandler.setResourceBase("src/main/resources/web");
            resourceHandler.setMinMemoryMappedContentLength(-1);
        } else {
            resourceHandler.setBaseResource(Resource.newClassPathResource("/web", true, false));
        }
        return resourceHandler;
    }

}