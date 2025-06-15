package com.phasetranscrystal.fpsmatch.core.network;

import okhttp3.*;
import okio.Buffer;
import okio.BufferedSource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * HTTP请求日志拦截器，用于记录请求和响应信息
 */
public class LoggingInterceptor implements Interceptor {
    private static final Logger logger = LoggerFactory.getLogger("FPSMatch HTTP Logger");
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    @Override
    public @NotNull Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        long startNs = System.nanoTime();

        // 记录请求信息
        StringBuilder requestLog = new StringBuilder();
        requestLog.append("\n");
        requestLog.append("--> ").append(request.method()).append(" ").append(request.url()).append("\n");

        // 添加请求头
        Headers requestHeaders = request.headers();
        for (int i = 0, count = requestHeaders.size(); i < count; i++) {
            String name = requestHeaders.name(i);
            // 跳过OkHttp自带的头信息
            if (!"Content-Type".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) {
                requestLog.append("| ").append(name).append(": ").append(requestHeaders.value(i)).append("\n");
            }
        }

        // 添加请求体
        RequestBody requestBody = request.body();
        if (requestBody != null) {
            Buffer buffer = new Buffer();
            requestBody.writeTo(buffer);
            Charset charset = UTF8;
            MediaType contentType = requestBody.contentType();
            if (contentType != null) {
                charset = contentType.charset(UTF8);
            }

            if (charset != null) {
                requestLog.append("| Request Body: ").append(buffer.readString(charset)).append("\n");
            }
        }

        requestLog.append("--> END ").append(request.method());
        logger.info(requestLog.toString());

        // 执行请求
        Response response = chain.proceed(request);

        // 记录响应信息
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        StringBuilder responseLog = new StringBuilder();
        responseLog.append("\n");
        responseLog.append("<-- ").append(response.code()).append(" ").append(response.message()).append(" ")
                .append(response.request().url()).append(" (").append(tookMs).append("ms)").append("\n");

        // 添加响应头
        Headers responseHeaders = response.headers();
        for (int i = 0, count = responseHeaders.size(); i < count; i++) {
            responseLog.append("| ").append(responseHeaders.name(i)).append(": ").append(responseHeaders.value(i)).append("\n");
        }

        // 添加响应体
        ResponseBody responseBody = response.body();
        if (responseBody != null) {
            long contentLength = responseBody.contentLength();
            BufferedSource source = responseBody.source();
            source.request(Long.MAX_VALUE); // Buffer the entire body.
            Buffer buffer = source.buffer();

            Charset charset = UTF8;
            MediaType contentType = responseBody.contentType();
            if (contentType != null) {
                try {
                    charset = contentType.charset(UTF8);
                } catch (Exception e) {
                    responseLog.append("| Couldn't decode the response body; charset is likely malformed.\n");
                    return response;
                }
            }

            if (contentLength != 0) {
                if (charset != null) {
                    responseLog.append("| Response Body: ").append(buffer.clone().readString(charset)).append("\n");
                }
            }
        }

        responseLog.append("<-- END HTTP");
        logger.info(responseLog.toString());

        return response;
    }
}