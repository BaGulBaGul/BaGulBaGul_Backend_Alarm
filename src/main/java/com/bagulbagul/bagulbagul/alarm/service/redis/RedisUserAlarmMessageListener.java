package com.bagulbagul.bagulbagul.alarm.service.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;
import reactor.core.publisher.Sinks.Many;

@Slf4j
public class RedisUserAlarmMessageListener implements MessageListener {
    private Many sink;

    public RedisUserAlarmMessageListener(Many sink) {
        this.sink = sink;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        sink.emitNext(message.toString(), (signalType, result) -> result == EmitResult.FAIL_NON_SERIALIZED);
    }
}
