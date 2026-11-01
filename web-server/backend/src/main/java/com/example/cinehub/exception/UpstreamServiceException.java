package com.example.cinehub.exception;

// Loi khi goi he thong ben ngoai (Elasticsearch, API quan tri cua Load Balancer).
// Mang theo ma HTTP muon tra ve cho frontend: 400/404/409 (LB tu choi), 502/503 (khong goi duoc).
public class UpstreamServiceException extends RuntimeException {
    private final int status;

    public UpstreamServiceException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}