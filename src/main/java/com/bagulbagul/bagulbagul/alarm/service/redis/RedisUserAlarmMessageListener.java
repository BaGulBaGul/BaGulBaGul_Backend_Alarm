package com.bagulbagul.bagulbagul.alarm.service.redis;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import reactor.core.publisher.Sinks.EmitResult;
import reactor.core.publisher.Sinks.Many;

public class RedisUserAlarmMessageListener implements MessageListener {
    private Many sink;
    private final int RETRY_CNT = 2;

    public RedisUserAlarmMessageListener(Many sink) {
        this.sink = sink;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        int failCnt = 0;
        EmitResult emitResult = sink.tryEmitNext(message.toString());
        while(emitResult.isFailure() && failCnt < RETRY_CNT) {
            failCnt += 1;
            sink.tryEmitNext(message.toString());
        }
    }
}
