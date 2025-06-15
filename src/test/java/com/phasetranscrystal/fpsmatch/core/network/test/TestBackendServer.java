package com.phasetranscrystal.fpsmatch.core.network.test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.network.example.ApiClientExample;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.Executors;

public class TestBackendServer {
    private static final int PORT = 8081;
    private HttpServer server;

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // 注册测试端点
        server.createContext("/users", new UserHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/echo", new EchoHandler());

        server.start();
        System.out.println("Test backend server started on port " + PORT);
        System.out.println("Available endpoints:");
        System.out.println("GET    /api/users/{id}");
        System.out.println("POST   /api/login");
        System.out.println("POST   /api/echo");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("Test backend server stopped");
        }
    }

    private static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestMethod = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] pathSegments = path.split("/");

            if ("GET".equals(requestMethod)) {
                // 处理GET请求: /users/{id} 或 /users?page=1&limit=20
                if (pathSegments.length >= 3 && !pathSegments[2].isEmpty()) {
                    // 获取单个用户
                    String userId = pathSegments[2];
                    String response = String.format(
                        "{\"id\": \"%s\", \"name\": \"Test User\", \"email\": \"user%s@example.com\", \"age\": 25}",
                        userId, userId
                    );
                    sendResponse(exchange, 200, response);
                } else {
                    // 获取用户列表，处理分页参数
                    Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
                    int page = Integer.parseInt(queryParams.getOrDefault("page", "1"));
                    int limit = Integer.parseInt(queryParams.getOrDefault("limit", "20"));
                    
                    // 模拟用户列表数据
                    StringBuilder response = new StringBuilder("[");
                    for (int i = 0; i < limit; i++) {
                        int userId = (page - 1) * limit + i + 1;
                        if (i > 0) response.append(",");
                        response.append(String.format(
                            "{\"id\": \"%d\", \"name\": \"User %d\", \"email\": \"user%d@example.com\", \"age\": %d}",
                            userId, userId, userId, 20 + (userId % 10)
                        ));
                    }
                    response.append("]");
                    sendResponse(exchange, 200, response.toString());
                }
            } else if ("POST".equals(requestMethod)) {
                // 处理POST请求: 创建新用户
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                // 简单模拟解析JSON
                String name = extractValue(requestBody, "name");
                String email = extractValue(requestBody, "email");
                int age = Integer.parseInt(extractValue(requestBody, "age", "25"));
                
                String newUserId = UUID.randomUUID().toString();
                String response = String.format(
                    "{\"id\": \"%s\", \"name\": \"%s\", \"email\": \"%s\", \"age\": %d}",
                    newUserId, name, email, age
                );
                sendResponse(exchange, 201, response); // 201 Created
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query == null || query.isEmpty()) return params;
            
            for (String pair : query.split("&") ) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
            return params;
        }

        private String extractValue(String json, String key) {
            return extractValue(json, key, "");
        }

        private String extractValue(String json, String key, String defaultValue) {
            String pattern = "\"" + key + "\":\"([^\"]+)\"";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            // 尝试匹配数字类型
            pattern = "\"" + key + "\":(\\d+)";
            p = Pattern.compile(pattern);
            m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            return defaultValue;
        }
    }

    private static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                // 读取请求体
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(requestBody);

                String username = params.getOrDefault("username", "");
                String token = "dummy-token-" + username.hashCode();
                ApiClientExample.User user = new ApiClientExample.User();
                user.setName(username);
                user.setAge(22);
                user.setEmail(username + "@example.com");
                user.setId("test");
                ApiClientExample.LoginResult result = new ApiClientExample.LoginResult();
                result.setToken(token);
                result.setUser(user);
                JsonElement jsonElement = ApiClientExample.LoginResult.CODEC.encodeStart(JsonOps.INSTANCE, result).getOrThrow(false, e -> {
                    throw new RuntimeException(e);
                });

                // 模拟登录响应
                String response = jsonElement.toString();

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    private static class EchoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                // 读取请求体并原样返回
                byte[] requestBody = exchange.getRequestBody().readAllBytes();

                exchange.getResponseHeaders().set("Content-Type", exchange.getRequestHeaders().getFirst("Content-Type"));
                exchange.sendResponseHeaders(200, requestBody.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(requestBody);
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    private static Map<String, String> parseFormData(String data) {
        Map<String, String> params = new HashMap<>();
        for (String pair : data.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
        return params;
    }

    public static void main(String[] args) throws IOException {
        TestBackendServer server = new TestBackendServer();
        server.start();

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}