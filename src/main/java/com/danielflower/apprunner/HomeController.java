package com.danielflower.apprunner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.client.HttpClient;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HomeController extends AbstractHandler {
    public static final Logger log = LoggerFactory.getLogger(HomeController.class);
    public static final Pattern APP_URL_PATTERN = Pattern.compile("/([^/]+)\\.html");
    private final HttpClient client;
    private TemplateEngine engine;
    private final Optional<String> appRunnerUrl;

    public HomeController(HttpClient client, TemplateEngine engine, Optional<String> appRunnerUrl) {
        this.client = client;
        this.engine = engine;
        this.appRunnerUrl = appRunnerUrl;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        WebContext context = new WebContext(request, response, baseRequest.getServletContext());
        String currentUrl = request.getScheme() + "://" + request.getHeader("Host");
        String appRunnerRestUrl = appRunnerUrl.orElse(currentUrl);
        log.info("Rest URL is " + appRunnerRestUrl + " - host is " + request.getHeader("Host"));

        Model model;
        Matcher appMatcher = APP_URL_PATTERN.matcher(target);
        try {
            if (target.equals("/")) {
                model = list(appRunnerRestUrl);
            } else if (target.equals("/getting-started")) {
                model = gettingStarted(appRunnerRestUrl);
            } else if (appMatcher.matches()) {
                String appName = appMatcher.group(1);
                model = viewApp(appName, appRunnerRestUrl);
            } else {
                return;
            }
        } catch (Exception e) {
            response.sendError(500, "Internal Server Error");
            log.error("Error while processing " + request.getRequestURI(), e);
            baseRequest.setHandled(true);
            return;
        }

        context.setVariables(model.variables);
        context.setVariable("host", currentUrl);
        context.setVariable("restUrl", appRunnerRestUrl);
        engine.process(model.viewName, context, response.getWriter());

        baseRequest.setHandled(true);
    }

    private Model list(String restBase) throws Exception {
        return model("home.html", jsonToMap(httpGet(restBase + "/api/v1/apps")));
    }

    private Model gettingStarted(String restBase) throws Exception {
        Map<String, Object> vars = jsonToMap(httpGet(restBase + "/api/v1/system"));
        try {
            File publicKey = new File(System.getProperty("user.home") + "/.ssh/id_rsa.pub");
            if (publicKey.isFile()) {
                vars.put("publicKey", FileUtils.readFileToString(publicKey));
            }
        } catch (IOException e) {
            log.info("Could not read the public key of this server", e);
        }
        return model("getting-started.html", vars);
    }

    private Model viewApp(String appName, String restBase) throws Exception {
        Map<String, Object> variables = new HashMap<>();
        variables.put("app", jsonToMap(httpGet(restBase + "/api/v1/apps/" + appName)));
        return model("app.html", variables);
    }

    private String httpGet(String uri) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("GET " + uri);
        return client.GET(uri).getContentAsString();
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
