package com.bagulbagul.bagulbagul.extension;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;

public class RedisTestContainerExtension implements BeforeAllCallback {

    private static final String REDIS_IMAGE = "redis:7.0.8-alpine";
    private static final int REDIS_PORT = 6379;

    private static GenericContainer REDIS_CONTAINER = new GenericContainer(REDIS_IMAGE)
            .withExposedPorts(REDIS_PORT);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if(!REDIS_CONTAINER.isRunning()) {
            REDIS_CONTAINER.start();
        }
        System.setProperty("spring.redis.host", REDIS_CONTAINER.getHost());
        System.setProperty("spring.redis.port", String.valueOf(REDIS_CONTAINER.getMappedPort(REDIS_PORT)));
    }
}
