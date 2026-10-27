package com.example.cinehub.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Converter
public class MediaUrlConverter implements AttributeConverter<String, String> {

    // "http(s)://<host bat ky>/uploads/..." -> lay phan "/uploads/..."
    private static final Pattern LOCAL_UPLOAD = Pattern.compile("^https?://[^/]+(/uploads/.*)$");

    /** Entity -> DB: chi luu duong dan tuong doi. Link ngoai (TMDB, YouTube...) giu nguyen. */
    @Override
    public String convertToDatabaseColumn(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = LOCAL_UPLOAD.matcher(url);
        return m.matches() ? m.group(1) : url;
    }

    /** DB -> Entity: ghep dia chi cua request hien tai vao truoc. */
    @Override
    public String convertToEntityAttribute(String dbValue) {
        String path = convertToDatabaseColumn(dbValue);   // don ca du lieu cu con luu tuyet doi
        if (path == null || !path.startsWith("/uploads/")) {
            return path;                                  // link ngoai: tra nguyen
        }
        if (RequestContextHolder.getRequestAttributes() == null) {
            return path;                                  // khong co HTTP request (job nen, test): de tuong doi
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(path)
                .toUriString();
    }
}