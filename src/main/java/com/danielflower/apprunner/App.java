package com.danielflower.apprunner;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.proxy.AsyncProxyServlet;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
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
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        try {
            Map<String, String> settings = System.getenv();
            String env = settings.getOrDefault("APP_ENV", "local"); // "prod" or "local"
            boolean isLocal = "local".equals(env);
            File dataDir = new File(settings.getOrDefault("APP_DATA", "target/data"));
            File tempDir = new File(settings.getOrDefault("TEMP", "target/temp"));

            // When run from app-runner, you must use the port set in the environment variable APP_PORT
            int port = Integer.parseInt(settings.getOrDefault("APP_PORT", "8081"));
            // All URLs must be prefixed with the app name, which is got via the APP_NAME env var.
            String appName = settings.getOrDefault("APP_NAME", "app-runner-home");

            start(isLocal, port, appName, dataDir, tempDir);
        } catch (Exception e) {
            log.error("Error on startup", e);
            System.exit(1);
        }
    }

    private static void start(boolean isLocal, int port, String appName, File dataDir, File tempDir) throws Exception {

        Server jettyServer = new Server();
        HttpConfiguration config = new HttpConfiguration();
        config.addCustomizer(new ForwardedRequestCustomizer());
        config.setOutputBufferSize(1024);
        HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(config);
        ServerConnector connector = new ServerConnector(jettyServer, httpConnectionFactory);
        connector.setPort(port);
        jettyServer.addConnector(connector);
        jettyServer.setStopAtShutdown(true);

        ContextHandlerCollection handlers = new ContextHandlerCollection();

        HttpClient client = new HttpClient(new SslContextFactory(true));
        client.start();

        TemplateEngine engine = createTemplateEngine(isLocal);

        String contextPath = "/" + appName;
        addScreenshotHandlerIfPhantomJSIsAvailable(new File(dataDir, "screenshots"), tempDir, handlers, contextPath);
        handlers.addHandler(toContext(new HomeController(client, engine), contextPath));
        handlers.addHandler(toContext(resourceHandler(isLocal), contextPath));
        handlers.addHandler(toContext(swaggerUIHandler(), contextPath + "/docs"));


        if (isLocal) {
            handlers.addHandler(createTRP("https://localhost:8443", client));
        }
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

    private static Handler createTRP(String target, HttpClient client) {
        ServletContextHandler servletContext = new ServletContextHandler();
        ServletHandler servletHandler = servletContext.getServletHandler();
        AsyncProxyServlet.Transparent rp = new AsyncProxyServlet.Transparent() {
            @Override
            protected HttpClient newHttpClient() {
                return client;
            }
        };
        ServletHolder proxy = new ServletHolder(rp);
        servletHandler.addServletWithMapping(proxy, "/*");
        proxy.setInitParameter("proxyTo", target);
        return servletContext;
    }


    private static ResourceHandler swaggerUIHandler() throws URISyntaxException {
        ResourceHandler rh = new ResourceHandler();
        String dirPath = "META-INF/resources/webjars/swagger-ui/3.20.9";
        URL swaggerHTMLResourceBase = App.class.getClassLoader().getResource(dirPath);
        if (swaggerHTMLResourceBase == null) {
            throw new RuntimeException("Could not find " + dirPath + " on classpath. It is expected to come from the swagger-ui jar");
        }
        rh.setResourceBase(swaggerHTMLResourceBase.toURI().toString());
        rh.setEtags(true);
        return rh;
    }

    private static void addScreenshotHandlerIfPhantomJSIsAvailable(File screenshotDir, File tempDir, ContextHandlerCollection handlers, String contextPath) throws IOException {
        String phantomjsBinPath = System.getenv("PHANTOMJS_BIN");
        if (phantomjsBinPath == null) {
            log.warn("No PHANTOMJS_BIN env var set, so no screenshots are available");
        } else {
            File phantomjsBin = new File(phantomjsBinPath);
            if (!phantomjsBin.isFile()) {
                log.warn("Could not find " + phantomjsBin.getCanonicalPath() + " so no screenshots are available");
            } else {
                log.info("Will use " + phantomjsBinPath + " to create screenshots in " + screenshotDir.getCanonicalPath());
                FileUtils.forceMkdir(screenshotDir);
                handlers.addHandler(toContext(new WebpageScreenshotHandler(screenshotDir, tempDir, phantomjsBin), contextPath));
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
        resourceHandler.setEtags(true);
        return resourceHandler;
    }

    private static ContextHandler toContext(Handler handler, String contextPath) {
        ContextHandler context = new ContextHandler();
        context.setContextPath(contextPath);
        context.setHandler(handler);
        return context;
    }

}