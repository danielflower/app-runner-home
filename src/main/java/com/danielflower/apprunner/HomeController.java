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

public class HomeController extends AbstractHandler {
    public static final Logger log = LoggerFactory.getLogger(HomeController.class);
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

        if (!target.equals("/"))
            return;

        WebContext context = new WebContext(request, response, request.getServletContext());

        String getAllUrl = appRunnerRestUrl + "/apps";
        String appsJson;
        try {
            appsJson = client.GET(getAllUrl).getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException("Error while getting " + getAllUrl, e);
        }
        Map<String, Object> retMap = new Gson().fromJson(appsJson, new TypeToken<HashMap<String, Object>>() {}.getType());
        context.setVariables(retMap);
        context.setVariable("host", request.getScheme() + "://" + request.getHeader("Host"));
        log.info("Apps map is " + retMap);

        engine.process("home.html", context, response.getWriter());

        baseRequest.setHandled(true);
    }
}
