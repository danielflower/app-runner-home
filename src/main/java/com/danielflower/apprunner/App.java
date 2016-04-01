package com.danielflower.apprunner;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
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
import java.net.URISyntaxException;
import java.net.URL;
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

        ContextHandlerCollection handlers = new ContextHandlerCollection();

        Optional<String> appRunnerRestUrlBase = isLocal ? Optional.of("http://localhost:8080") : Optional.empty();
        HttpClient client = new HttpClient(new SslContextFactory(true));
        client.start();

        TemplateEngine engine = createTemplateEngine(isLocal);

        String contextPath = "/" + appName;
        addScreenshotHandlerIfPhantomJSIsAvailable(tempDir, handlers, contextPath);
        handlers.addHandler(toContext(new HomeController(client, engine, appRunnerRestUrlBase), contextPath));
        handlers.addHandler(toContext(resourceHandler(isLocal), contextPath));
        handlers.addHandler(toContext(swaggerUIHandler(), contextPath + "/docs"));

        jettyServer.setHandler(handlers);

        try {
            jettyServer.start();
        } catch (Throwable e) {
            log.error("Error on start", e);
            System.exit(1);
        }

        log.info("Started " + appName + " at http://localhost:" + port + contextPath);

        jettyServer.join();
    }

    private static ResourceHandler swaggerUIHandler() throws URISyntaxException {
        ResourceHandler rh = new ResourceHandler();
        String dirPath = "META-INF/resources/webjars/swagger-ui/2.1.4";
        URL swaggerHTMLResourceBase = App.class.getClassLoader().getResource(dirPath);
        if (swaggerHTMLResourceBase == null) {
            throw new RuntimeException("Could not find " + dirPath + " on classpath. It is expected to come from the swagger-ui jar");
        }
        rh.setResourceBase(swaggerHTMLResourceBase.toURI().toString());
        rh.setEtags(true);
        return rh;
    }

    private static void addScreenshotHandlerIfPhantomJSIsAvailable(File dataDir, ContextHandlerCollection handlers, String contextPath) throws IOException {
        String phantomjsBinPath = System.getenv("PHANTOMJS_BIN");
        if (phantomjsBinPath == null) {
            log.warn("No PHANTOMJS_BIN env var set, so no screenshots are available");
        } else {
            File phantomjsBin = new File(phantomjsBinPath);
            if (!phantomjsBin.isFile()) {
                log.warn("Could not find " + phantomjsBin.getCanonicalPath() + " so no screenshots are available");
            } else {
                handlers.addHandler(toContext(new WebpageScreenshotHandler(dataDir, phantomjsBin), contextPath));
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

    private static ResourceHandler resourceHandler(boolean useFileSystem) {
        ResourceHandler resourceHandler = new ResourceHandler();
        if (useFileSystem) {
            resourceHandler.setResourceBase("src/main/resources/web");
            resourceHandler.setMinMemoryMappedContentLength(-1);
        } else {
            resourceHandler.setBaseResource(Resource.newClassPathResource("/web", true, false));
        }
        return resourceHandler;
    }

    private static ContextHandler toContext(Handler handler, String contextPath) {
        ContextHandler context = new ContextHandler();
        context.setContextPath(contextPath);
        context.setHandler(handler);
        return context;
    }

}