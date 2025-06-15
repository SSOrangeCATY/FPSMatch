package com.phasetranscrystal.fpsmatch.core.network;

import com.mojang.serialization.Codec;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

/**
 * 网络请求模块核心类，负责管理OkHttpClient实例和提供请求构建接口
 */
public class NetworkModule {
    private final OkHttpClient okHttpClient;
    private final String baseUrl;

    /**
     * 私有构造函数，通过Builder初始化
     */
    private NetworkModule(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(builder.connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(builder.readTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(builder.writeTimeout, TimeUnit.MILLISECONDS)
                .addInterceptor(new LoggingInterceptor()) // 添加日志拦截器
                .build();
    }

    /**
     * 创建POST请求构建器
     */
    public <T> RequestBuilder<T> newRequest(Codec<T> codec) {
        return new RequestBuilder<>(okHttpClient, codec)
                .setUrl(baseUrl);
    }

    /**
     * 获取基础URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 获取OkHttpClient实例
     */
    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    /**
     * 构建器类，用于配置NetworkModule
     */
    public static class Builder {
        private String baseUrl = "";
        private long connectTimeout = 10_000;
        private long readTimeout = 10_000;
        private long writeTimeout = 10_000;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder connectTimeout(long timeout, TimeUnit unit) {
            this.connectTimeout = unit.toMillis(timeout);
            return this;
        }

        public Builder readTimeout(long timeout, TimeUnit unit) {
            this.readTimeout = unit.toMillis(timeout);
            return this;
        }

        public Builder writeTimeout(long timeout, TimeUnit unit) {
            this.writeTimeout = unit.toMillis(timeout);
            return this;
        }

        public NetworkModule build() {
            return new NetworkModule(this);
        }
    }
}