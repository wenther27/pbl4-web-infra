package com.example.cinehub.exception;

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