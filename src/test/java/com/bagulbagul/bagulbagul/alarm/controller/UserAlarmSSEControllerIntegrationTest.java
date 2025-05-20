package com.bagulbagul.bagulbagul.alarm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

import com.bagulbagul.bagulbagul.extension.AllTestContainerExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@ExtendWith(AllTestContainerExtension.class)
@AutoConfigureWebTestClient(timeout = "PT300S")
@SpringBootTest
class UserAlarmSSEControllerIntegrationTest {

    @Autowired
    WebTestClient client;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @Value("${alarm.realtime.redis.alarm_topic_prefix}")
    private String ALARM_TOPIC_PREFIX;

    @Value("${alarm.realtime.hb_message}")
    private String HB_MESSAGE;

    /*
     * 동일 userId에 대한 sse 순간 연결 반복 테스트
     */
    @Test
    @DisplayName("userId에 대한 sse 연결 테스트")
    public void test(){

        String message = "test";
        Long userId = 1L;

        Flux<String> sse = getAlarmSSE(userId);
        //처음 HB를 받는다면 sse 준비 완료. 알람 redis 메세지 발행
        sse = sse.doOnEach(stringSignal -> {
            if(HB_MESSAGE.equals(stringSignal.get())) {
                    redisTemplate.convertAndSend(ALARM_TOPIC_PREFIX + userId, message);
            }
        })
        //HB가 아닌 것만 받는다
        .filter(s -> !s.equals(HB_MESSAGE));

        String alarmMessage = sse.blockFirst(Duration.ofSeconds(10));
        assertThat(alarmMessage).isEqualTo(message);
    }

    /*
     * 동일 userId에 대한 sse 순간 연결 반복 테스트
     */
    @RepeatedTest(1000)
    @DisplayName("동일 userId에 대한 sse 순간 연결 반복 테스트")
    public void test_repeat(){

        String message = "test";
        Long userId = 1L;

        Flux<String> sse = getAlarmSSE(userId);
        //처음 HB를 받는다면 sse 준비 완료. 알람 redis 메세지 발행
        sse = sse.doOnEach(stringSignal -> {
            if(HB_MESSAGE.equals(stringSignal.get())) {
                redisTemplate.convertAndSend(ALARM_TOPIC_PREFIX + userId, message);
            }
        })
        //HB가 아닌 것만 받는다
        .filter(s -> !s.equals(HB_MESSAGE));

        String alarmMessage = sse.blockFirst(Duration.ofSeconds(10));
        assertThat(alarmMessage).isEqualTo(message);
    }

    @Test
    @DisplayName("동일 userId에 대한 sse 동시 연결 반복 테스트")
    public void test_concurrent() throws InterruptedException, ExecutionException, TimeoutException {
        int repeatCnt = 20;
        int threadCnt = 5;
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Void>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(threadCnt);
        for(int i=0; i<threadCnt; i++) {
            futures.add(executorService.submit(() -> {
                //모든 스레드가 동시에 시작하도록 대기
                startLatch.await();
                for(int r=0; r<repeatCnt; r++) {
                    //테스트 수행. 예외 발생 시 future로 전파.
                    test();
                }
                //문제가 없다면 null 반환
                return null;
            }));
        }
        Thread.sleep(1000);

        startLatch.countDown();

        for(int i=0; i<threadCnt; i++) {
            // 스레드들의 오류를 전파받는다. 문제가 없다면 null 반환
            futures.get(i).get(100, TimeUnit.SECONDS);
        }
        executorService.shutdown();
    }

    /*
     * 동일 userId에 대한 sse 동시 연결 + redis 메세지 동시 발행 경합 테스트
     */
    @Test
    @DisplayName("동일 userId에 대한 sse 동시 연결 + redis 메세지 동시 발행 경합 테스트")
    void test_multiple_sse_and_redis_race_condition() throws InterruptedException {

        int connectionCnt = 10;
        int messageCnt = 10;
        int totalMessageCnt = connectionCnt * messageCnt;
        CountDownLatch latch = new CountDownLatch(totalMessageCnt);

        Long userId = 1L;
        String message = "test";
        List<Flux<String>> sseList = new ArrayList<>();

        //연결 생성
        for(int i=0; i<connectionCnt; i++) {
            Flux<String> sse = getAlarmSSEExceptHB(userId);
            sse.subscribe(s -> {
                assertThat(s).isEqualTo(message);
                latch.countDown();
            });
            sseList.add(sse);
        }

        //redis 메세지 10개를 순간적으로 발행한다. 스레드 경합에 대한 처리가 되어있지 않으면 오류 발생.
        for(int i=0; i<messageCnt; i++) {
            redisTemplate.convertAndSend(ALARM_TOPIC_PREFIX + userId, message);
        }

        boolean result = latch.await(20, TimeUnit.SECONDS);
        assertThat((int)(totalMessageCnt - latch.getCount())).isEqualTo(totalMessageCnt);
    }

    /*
     * 동일 userId에 대한 sse 동시 연결 + 연결 해제, 구독자 감소 테스트
     * 연결 종료가 잘 되는지 확인 + 연결 종료 이후에도 다른 sse 연결이 잘 작동하는지 확인
     */
    @Test
    @DisplayName("동일 userId에 대한 sse 동시 연결 + 연결 해제, 구독 취소 테스트")
    void test_multiple_sse_and_cancle_subscribtion() throws InterruptedException {
        int connectionCnt = 10;
        int messageCnt = connectionCnt;
        int totalMessageCnt = (messageCnt * (messageCnt + 1)) / 2;
        CountDownLatch latch = new CountDownLatch(totalMessageCnt);

        Long userId = 1L;
        String message = "test";
        List<Flux<String>> sseList = new ArrayList<>();
        List<Disposable> sseDisposableList = new ArrayList<>();

        //연결 생성
        for(int i=0; i<connectionCnt; i++) {
            Flux<String> sse = getAlarmSSEExceptHB(userId);
            Disposable sseDisposable = sse.subscribe(s -> {
                assertThat(s).isEqualTo(message);
                latch.countDown();
            });
            sseList.add(sse);
            sseDisposableList.add(sseDisposable);
        }

        //redis 메세지 발행
        for(int i=0; i<connectionCnt; i++) {
            redisTemplate.convertAndSend(ALARM_TOPIC_PREFIX + userId, message);
            //redis 메세지가 전달되기 전에 dispose 되는 것을 방지하기 위해 충분히 대기
            Thread.sleep(1000);
            //연결 종료
            sseDisposableList.get(i).dispose();
            //연결 종료가 잘 되는지 보기 위한 것이므로 연결이 종료될 때까지 충분히 대기
            Thread.sleep(1000);
        }

        latch.await(20, TimeUnit.SECONDS);
        assertThat((int)(totalMessageCnt - latch.getCount())).isEqualTo(totalMessageCnt);
    }

    private Flux<String> getAlarmSSE(Long userId) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(userId, null);
        auth.setAuthenticated(true);

        Flux<String> sse = client
                .mutateWith(mockAuthentication(auth))
                .get()
                .uri("/alarm/subscribe")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .getResponseBody()
                .map(ServerSentEvent::data);
        return sse;
    }

    private Flux<String> getAlarmSSEExceptHB(Long userId) {
        return getAlarmSSE(userId).filter(s -> !s.equals(HB_MESSAGE));
    }
}