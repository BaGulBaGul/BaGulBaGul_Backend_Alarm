package com.bagulbagul.bagulbagul.alarm.service;

import reactor.core.publisher.Flux;

public interface UserAlarmSubscribeManager {
    Flux<String> subscribe(Long userId);
}
