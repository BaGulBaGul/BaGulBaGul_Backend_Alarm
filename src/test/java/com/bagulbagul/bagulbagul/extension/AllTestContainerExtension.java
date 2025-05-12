package com.bagulbagul.bagulbagul.extension;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class AllTestContainerExtension implements BeforeAllCallback {

    static private final RedisTestContainerExtension redis = new RedisTestContainerExtension();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        redis.beforeAll(context);
    }
}