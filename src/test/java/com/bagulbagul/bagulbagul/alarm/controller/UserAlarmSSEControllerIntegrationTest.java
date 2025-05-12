package com.bagulbagul.bagulbagul.alarm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

import com.bagulbagul.bagulbagul.extension.AllTestContainerExtension;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@ExtendWith(AllTestContainerExtension.class)
@AutoConfigureWebTestClient(timeout = "PT600S")
@SpringBootTest
class UserAlarmSSEControllerIntegrationTest {

    @Autowired
    WebTestClient client;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    private final String ALARM_TOPIC_PREFIX = "alarm_user_";

    @Test
    public void test() {
        //유저 정보
        Long userId = 1l;
        TestingAuthenticationToken auth = new TestingAuthenticationToken(1L, null);
        auth.setAuthenticated(true);

        //sse 구독
        Flux<ServerSentEvent<String>> sse = client
                .mutateWith(mockAuthentication(auth))
                .get()
                .uri("/alarm/subscribe")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .getResponseBody();

        //redis에 메세지 발행
        String message = "test";
        redisTemplate.convertAndSend(ALARM_TOPIC_PREFIX + userId, message);

        //sse 알람 메세지 수신
        String alarmMessage = sse
                .map(ServerSentEvent::data)
                .filter(s -> !s.equals("HB"))
                .take(1)
                .blockFirst();

        //메세지 내용 확인
        assertThat(alarmMessage).isEqualTo(message);
    }
}