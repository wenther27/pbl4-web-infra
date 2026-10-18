package com.pbl4.anomaly.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Goi REST API cua Elasticsearch: gui cau truy van JSON, nhan ket qua JSON.
// Khong dung thu vien client chinh thuc cua Elastic -> it phu thuoc, de hieu tung buoc.
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
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + index + "/_search"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(queryJson))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Elasticsearch tra ve " + response.statusCode() + ": " + response.body());
        }
        return mapper.readTree(response.body());
    }
}