package com.danielflower.apprunner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HomeController extends AbstractHandler {
    private static final Logger log = LoggerFactory.getLogger(HomeController.class);
    private static final Pattern APP_URL_PATTERN = Pattern.compile("/([^/]+)\\.html");
    private final HttpClient client;
    private TemplateEngine engine;

    public HomeController(HttpClient client, TemplateEngine engine) {
        this.client = client;
        this.engine = engine;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        WebContext context = new WebContext(request, response, baseRequest.getServletContext());
        String currentBaseUrl = request.getScheme() + "://" + request.getHeader("Host");

        Model model;
        Matcher appMatcher = APP_URL_PATTERN.matcher(target);
        try {
            if (target.equals("/")) {
                model = list(currentBaseUrl);
            } else if (target.equals("/getting-started")) {
                model = gettingStarted(currentBaseUrl);
            } else if (target.equals("/system")) {
                model = systemInfo(currentBaseUrl);
            } else if (target.equals("/docs/api.html")) {
                model = swaggerDocs();
            } else if (appMatcher.matches()) {
                String appName = appMatcher.group(1);
                model = viewApp(appName, currentBaseUrl);
            } else {
                return;
            }
        } catch (Exception e) {
            response.sendError(500, "Internal Server Error");
            log.error("Error while processing " + request.getRequestURI(), e);
            baseRequest.setHandled(true);
            return;
        }

        response.setHeader("X-UA-Compatible", "IE=edge");

        model.variables.put("baseUrl", currentBaseUrl);
        context.setVariables(model.variables);
        engine.process(model.viewName, context, response.getWriter());

        baseRequest.setHandled(true);
    }

    private Model list(String restBase) throws Exception {
        Map<String, Object> apps = jsonToMap(httpGet(restBase + "/api/v1/apps"));
        List<Map<String,Object>> all = (List<Map<String, Object>>) apps.get("apps");
        Predicate<Map<String, Object>> isAvailable = app -> (boolean)app.get("available");
        List<Map<String, Object>> running = all.stream().filter(isAvailable).collect(Collectors.toList());
        List<Map<String, Object>> notRunning = all.stream().filter(isAvailable.negate()).collect(Collectors.toList());
        return model("home.html", new HashMap<String, Object>() {{
            put("apps", running);
            put("appCount", all.size());
            put("notRunning", notRunning);
            put("errors", apps.get("errors"));
        }});
    }

    private Model gettingStarted(String restBase) throws Exception {
        Map<String, Object> vars = jsonToMap(httpGet(restBase + "/api/v1/system"));
        try {
            File publicKey = new File(System.getProperty("user.home") + "/.ssh/id_rsa.pub");
            if (publicKey.isFile()) {
                vars.put("publicKey", FileUtils.readFileToString(publicKey, "UTF-8"));
            }
        } catch (IOException e) {
            log.info("Could not read the public key of this server", e);
        }
        return model("getting-started.html", vars);
    }

    private Model systemInfo(String restBase) throws Exception {
        Map<String, Object> vars = jsonToMap(httpGet(restBase + "/api/v1/system"));
        vars.put("instanceHost", vars.get("host")); // "host" gets overwritten elsewhere, so renaming here
        String view = vars.containsKey("runners") ? "system-info-with-router.html" : "system-info.html";
        return model(view, vars);
    }


    private Model swaggerDocs() throws Exception {
        return model("swagger-docs.html", new HashMap<>());
    }

    private Model viewApp(String appName, String restBase) throws Exception {
        Map<String, Object> variables = new HashMap<>();
        variables.put("app", jsonToMap(httpGet(restBase + "/api/v1/apps/" + appName)));
        return model("app.html", variables);
    }

    private String httpGet(String uri) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("GET " + uri);
        ContentResponse response = client.GET(uri);
        String content = response.getContentAsString();
        if (response.getStatus() != 200) {
            throw new RuntimeException(response.getStatus() + " while loading " + uri + ". Content was: " + content);
        }
        return content;
    }

    private static Map<String, Object> jsonToMap(String appsJson) {
        return new Gson().fromJson(appsJson, new TypeToken<HashMap<String, Object>>() {}.getType());
    }

    static private class Model {
        public final String viewName;
        public final Map<String, Object> variables;
        public Model(String viewName, Map<String, Object> variables) {
            this.viewName = viewName;
            this.variables = variables;
        }
    }
    private static Model model(String view, Map<String, Object> variables) {
        return new Model(view, variables);
    }
}
