package com.intellipolis.common.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class ExternalHttp {
    private ExternalHttp() {}

    public static RestClient create() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        return RestClient.builder().requestFactory(factory).build();
    }
}
