package com.pbl4.anomaly.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ElasticsearchClient {

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public ElasticsearchClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public JsonNode search(String index, String queryJson) throws IOException, InterruptedException {
        return send("POST", "/" + index + "/_search", queryJson);
    }

    public JsonNode index(String index, Object doc) throws IOException, InterruptedException {
        return send("POST", "/" + index + "/_doc", mapper.writeValueAsString(doc));
    }

    public JsonNode putIndexTemplate(String name, String templateJson) throws IOException, InterruptedException {
        return send("PUT", "/_index_template/" + name, templateJson);
    }

    private JsonNode send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Elasticsearch tra ve " + code + ": " + response.body());
        }
        return mapper.readTree(response.body());
    }
}