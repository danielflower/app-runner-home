package com.danielflower.apprunner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HomeController extends AbstractHandler {
    public static final Logger log = LoggerFactory.getLogger(HomeController.class);
    public static final Pattern APP_URL_PATTERN = Pattern.compile("/([^/]+)\\.html");
    private final String appRunnerRestUrl;
    private final HttpClient client;
    private TemplateEngine engine;

    public HomeController(String appRunnerRestUrl, HttpClient client, TemplateEngine engine) {
        this.appRunnerRestUrl = appRunnerRestUrl;
        this.client = client;
        this.engine = engine;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        WebContext context = new WebContext(request, response, request.getServletContext());
        Model model;
        Matcher appMatcher = APP_URL_PATTERN.matcher(target);
        try {
            if (target.equals("/")) {
                model = list();
            } else if (appMatcher.matches()) {
                String appName = appMatcher.group(1);
                model = viewApp(appName);
            } else {
                return;
            }
        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("Error while processing " + request.getRequestURI() + ": " + e);
            baseRequest.setHandled(true);
            return;
        }

        context.setVariables(model.variables);
        context.setVariable("host", context.getRequest().getScheme() + "://" + context.getRequest().getHeader("Host"));
        engine.process(model.viewName, context, response.getWriter());

        baseRequest.setHandled(true);
    }

    private Model list() throws Exception {
        return model("home.html", jsonToMap(httpGet("/apps")));
    }

    private Model viewApp(String appName) throws Exception {
        Map<String, Object> variables = new HashMap<>();
        variables.put("app", jsonToMap(httpGet("/apps/" + appName)));
        return model("app.html", variables);
    }

    private String httpGet(String relativeUrl) throws InterruptedException, ExecutionException, TimeoutException {
        String uri = appRunnerRestUrl + relativeUrl;
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
