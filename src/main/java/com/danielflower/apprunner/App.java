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

import java.net.InetSocketAddress;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

public class App {
    public static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {

        String env = firstNonNull(System.getenv("APP_ENV"), "local"); // "prod" or "local"
        boolean isLocal = "local".equals(env);

        // When run from app-runner, you must use the port set in the environment variable APP_PORT
        int port = Integer.parseInt(firstNonNull(System.getenv("APP_PORT"), "8081"));
        // All URLs must be prefixed with the app name, which is got via the APP_NAME env var.
        String appName = firstNonNull(System.getenv("APP_NAME"), "app-runner-home");

        Server jettyServer = new Server(new InetSocketAddress("localhost", port));
        jettyServer.setStopAtShutdown(true);

        HandlerList handlers = new HandlerList();

        String appRunnerRestUrlBase = (firstNonNull(System.getenv("APP_REST_URL_BASE_V1"), "http://localhost:8080/api/v1"));
        HttpClient client = new HttpClient(new SslContextFactory(true));
        client.start();

        TemplateEngine engine = createTemplateEngine(isLocal);

        handlers.addHandler(new HomeController(appRunnerRestUrlBase, client, engine));
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