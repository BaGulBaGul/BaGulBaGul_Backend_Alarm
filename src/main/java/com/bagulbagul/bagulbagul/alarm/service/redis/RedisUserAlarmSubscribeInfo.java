package com.bagulbagul.bagulbagul.alarm.service.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.redis.connection.MessageListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RedisUserAlarmSubscribeInfo {
    private Long userId;
    private MessageListener messageListener;
    private Sinks.Many<String> sink;
    private Flux<String> flux;
    private int subscribeCnt;
}
