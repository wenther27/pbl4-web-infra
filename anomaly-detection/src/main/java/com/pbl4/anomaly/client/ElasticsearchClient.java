package com.pbl4.anomaly.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Goi REST API cua Elasticsearch: gui JSON, nhan JSON.
// Buoi 1: search (doc). Buoi 3: them index (ghi 1 tai lieu) va putIndexTemplate.
public class ElasticsearchClient {

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public ElasticsearchClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    // POST /<index>/_search voi body la cau truy van
    public JsonNode search(String index, String queryJson) throws IOException, InterruptedException {
        return send("POST", "/" + index + "/_search", queryJson);
    }

    // POST /<index>/_doc : ghi 1 tai lieu, Elasticsearch tu sinh _id.
    // doc la doi tuong Java bat ky (Map, record...), Jackson doi sang JSON.
    public JsonNode index(String index, Object doc) throws IOException, InterruptedException {
        return send("POST", "/" + index + "/_doc", mapper.writeValueAsString(doc));
    }

    // PUT /_index_template/<name> : khai bao kieu du lieu cho cac index sau nay.
    // Goi lai nhieu lan van an toan (ghi de template cu bang ban giong het).
    public JsonNode putIndexTemplate(String name, String templateJson) throws IOException, InterruptedException {
        return send("PUT", "/_index_template/" + name, templateJson);
    }

    // Phan chung: gui request, kiem tra ma tra ve, doi body thanh cay JSON
    private JsonNode send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        if (code < 200 || code >= 300) {   // 200 OK, 201 Created deu la thanh cong
            throw new IOException("Elasticsearch tra ve " + code + ": " + response.body());
        }
        return mapper.readTree(response.body());
    }
}