package com.example.cinehub.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Converter
public class MediaUrlConverter implements AttributeConverter<String, String> {

    private static final Pattern LOCAL_UPLOAD = Pattern.compile("^https?://[^/]+(/uploads/.*)$");

    @Override
    public String convertToDatabaseColumn(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = LOCAL_UPLOAD.matcher(url);
        return m.matches() ? m.group(1) : url;
    }

    @Override
    public String convertToEntityAttribute(String dbValue) {
        String path = convertToDatabaseColumn(dbValue);
        if (path == null || !path.startsWith("/uploads/")) {
            return path;
        }
        if (RequestContextHolder.getRequestAttributes() == null) {
            return path;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(path)
                .toUriString();
    }
}