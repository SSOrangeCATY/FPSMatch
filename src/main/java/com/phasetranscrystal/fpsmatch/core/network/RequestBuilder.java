package com.phasetranscrystal.fpsmatch.core.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 请求构建器，提供链式API配置请求参数
 */
public class RequestBuilder<T> {
    private final OkHttpClient client;
    private String url = "";
    private RequestMethod method = RequestMethod.GET;
    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> queryParams = new HashMap<>();
    private RequestBody requestBody;
    private TimeUnit timeoutUnit;
    private final Codec<T> codec;

    public RequestBuilder(OkHttpClient client,Codec<T> codec) {
        this.client = client;
        this.codec = codec;
    }

    public RequestBuilder<T> setRequestMethod(RequestMethod method){
        this.method = method;
        return this;
    }

    public RequestBuilder<T> setUrl(String url){
        this.url = url;
        return this;
    }

    public RequestBuilder<T> addPath(String path){
        if (path == null || path.isEmpty()) {
            return this;
        }
        
        if (!this.url.endsWith("/")) {
            this.url += "/";
        }
        
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        this.url += path;
        return this;
    }

    /**
     * 添加请求头
     */
    public RequestBuilder<T> addHeader(String key, String value) {
        headers.put(key, value);
        return this;
    }

    /**
     * 添加URL查询参数
     */
    public RequestBuilder<T> addQueryParam(String key, String value) {
        queryParams.put(key, value);
        return this;
    }

    /**
     * 设置JSON请求体
     */
    public RequestBuilder<T> setJsonBody(T body) {
        try {
            JsonElement jsonElement = codec.encodeStart(JsonOps.INSTANCE, body).getOrThrow(false, e -> {
                throw new RuntimeException(e);
            });
            this.requestBody = RequestBody.create(
                jsonElement.toString(),
                MediaType.parse("application/json; charset=utf-8")
            );
        } catch (Exception e) {
            throw new RuntimeException("设置请求体失败: " + e.getMessage(), e);
        }
        return this;
    }

    /**
     * 设置表单请求体
     */
    public RequestBuilder<T> setFormBody(Map<String, String> formData) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            formBuilder.add(entry.getKey(), entry.getValue());
        }
        this.requestBody = formBuilder.build();
        return this;
    }


    /**
     * 同步执行请求
     */
    public ApiResponse<T> execute() throws IOException {
        Request request = buildRequest();
        Response response = client.newCall(request).execute();
        return parseResponse(response);
    }

    /**
     * 异步执行请求
     */
    public void enqueue(Callback<T> callback) {
        try {
            Request request = buildRequest();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    try (response) {
                        ApiResponse<T> apiResponse = parseResponse(response);
                        callback.onResponse(apiResponse);
                    } catch (Exception e) {
                        callback.onFailure(e);
                    }
                }
            });
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    /**
     * 构建OkHttp请求对象
     */
    private Request buildRequest() {
        if(url.isEmpty()){
            throw new IllegalArgumentException("url is empty");
        }

        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();

        // 添加查询参数
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(urlBuilder.build());

        // 添加请求头
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder.addHeader(entry.getKey(), entry.getValue());
        }

        // 设置请求方法和请求体
        switch (method) {
            case GET:
                requestBuilder.get();
                break;
            case POST:
                requestBuilder.post(requestBody != null ? requestBody : RequestBody.create(new byte[0]));
                break;
            case PUT:
                requestBuilder.put(requestBody != null ? requestBody : RequestBody.create(new byte[0]));
                break;
            case DELETE:
                if (requestBody != null) {
                    requestBuilder.delete(requestBody);
                } else {
                    requestBuilder.delete();
                }
                break;
            case HEAD:
                requestBuilder.head();
                break;
            case OPTIONS:
                requestBuilder.method("OPTIONS", requestBody);
                break;
            case PATCH:
                requestBuilder.patch(requestBody != null ? requestBody : RequestBody.create(new byte[0]));
                break;
        }

        return requestBuilder.build();
    }

    /**
     * 解析响应数据
     */
    private ApiResponse<T> parseResponse(Response response) throws IOException {
        ApiResponse<T> apiResponse = new ApiResponse<>();
        apiResponse.setCode(response.code());
        apiResponse.setMessage(response.message());
        apiResponse.setHeaders(response.headers().toMultimap());

        ResponseBody body = response.body();
        if (body != null) {
            String responseBody = body.string();
            apiResponse.setRawBody(responseBody);

            // 如果设置了Codec且响应码成功，则解析JSON
        if (codec != null && response.isSuccessful()) {
            try {
                JsonElement jsonElement = JsonParser.parseString(responseBody);
                T data = codec.decode(JsonOps.INSTANCE, jsonElement)
                    .getOrThrow(false, e -> {
                        throw new RuntimeException(e);
                    }).getFirst();
                apiResponse.setData(data);
            } catch (Exception e) {
                apiResponse.setError(new ApiError("CODEC解码失败: " + e.getMessage(), e));
            }
        }

        }

        if (!response.isSuccessful()) {
            apiResponse.setError(new ApiError("请求失败: " + response.code() + " " + response.message()));
        }

        return apiResponse;
    }



    /**
     * 异步请求回调接口
     */
    public interface Callback<T> {
        void onResponse(ApiResponse<T> response);
        void onFailure(Throwable throwable);
    }
}