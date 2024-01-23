package com.bagulbagul.bagulbagul.alarm.controller;

import reactor.core.publisher.Flux;

public interface UserAlarmSSEController {
    Flux<String> subscribe(Long userId);
}
