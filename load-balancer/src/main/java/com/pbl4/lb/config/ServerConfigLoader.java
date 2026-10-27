package com.pbl4.lb.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

//Đọc danh sách BE từ servers.yaml

public class ServerConfigLoader {
    // Ket qua docj cau hinh: port LB nghe va danh sach BE
    public record Config(int port, List<String> backends) {

    }

    public static Config load() {
        Map<String, Object> data;
        try (InputStream in = ServerConfigLoader.class.getResourceAsStream("/servers.yaml")) {
            if (in == null)
                throw new IllegalStateException("Khong tim thay servers.yaml");
            data = new Yaml().load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        int port = ((Number)data.getOrDefault("port", 8080)).intValue();

        //Lấy giá trị thô
        Object raw = data.get("backends");
        if(!(raw instanceof List<?> list) || list.isEmpty())
            //pattern matching kiem tra raw co phai list ? đúng thì tạo list ép kiểu sẵn
            throw new IllegalStateException("servers.yaml phai co danh sach backends");

        List<String> backends = list.stream().map(Object::toString).toList();

        String envBackends = System.getenv("LB_BACKENDS");
        if (envBackends != null && !envBackends.isBlank()) {
            backends = Arrays.stream(envBackends.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        String envPort = System.getenv("LB_PORT");
        if (envPort != null && !envPort.isBlank())
            port = Integer.parseInt(envPort.trim());

        return new Config(port, List.copyOf(backends));
        //tạo bản sao không sửa được
    }
}