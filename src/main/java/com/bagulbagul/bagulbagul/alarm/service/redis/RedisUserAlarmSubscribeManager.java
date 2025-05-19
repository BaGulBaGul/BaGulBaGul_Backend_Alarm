package com.bagulbagul.bagulbagul.alarm.service.redis;

import com.bagulbagul.bagulbagul.alarm.service.UserAlarmSubscribeManager;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisUserAlarmSubscribeManager implements UserAlarmSubscribeManager {

    //redis 채널명의 prefix
    @Value("${alarm.realtime.redis.alarm_topic_prefix}")
    private String TOPIC_PREFIX;

    //HeartBeat 요청의 간격(초)
    @Value("${alarm.realtime.hb_interval_second}")
    private int HEARTBEAT_INTERVAL_SECOND;
    //HeartBeat 요청 메세지 내용
    @Value("${alarm.realtime.hb_message}")
    private String HEARTBEAT_MESSAGE;

    //레디스 메세지 리스너 관리 컨테이너
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    // user Id -> 등록정보
    private final ConcurrentHashMap<Long, RedisUserAlarmSubscribeInfo> subscribeInfoMap = new ConcurrentHashMap<>();

    //주기적으로 heartbeat 를 보내서 연결이 끊겼는지 확인한다
    //single thread 의 이유
    //1.같은 동작을 반복하므로 캐시 등의 효율성을 위해 한 스레드에서 실행하도록 함.
    //2.연결 정리보다 다른 메세지 전달이 더 중요하기 때문에 다른 Flux 를 방해하지 않기 위해 스레드 제한을 뒀다.
    private Flux<String> heartbeatFlux;


    @PostConstruct
    private void init() {
        heartbeatFlux = Flux.interval(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECOND), Schedulers.single())
                .map(i -> HEARTBEAT_MESSAGE);
    }

    /*
     * flow : redis_pub/sub -> redis_message_listener -> sink -> flux
     * 1. userId에 연결된 구독 정보가 없으면 등록 후 연결된 Flux 객체 반환
     * 2. Flux를 구독하면 구독자 수 1 증가, 구독 해제하면 구독자 수 1 감소
     * 3. 구독자 수가 1 이상에서 0이 되면 레디스 리스너 해제, 구독 정보 삭제.
     */
    @Override
    public Flux<String> subscribe(final Long userId) {

        /*
         * userId에 대한 구독 정보가 없다면 등록. 구독 정보를 가져온다.
         * 등록 과정에서 이벤트 루프의 block을 방지하기 위해 작업 스레드에서 처리
         *
         * 구독자 수는 동기화된 코드 내에서 구독 정보 반환 직전에 즉시 올려줘야 한다.
         * 만약 doOnSubscribe에서 구독자 수를 증가시키면 기존 연결이 닫히면서 구독자가 0이 되고 자원이 정리될 수 있다.
         * req1 : subscribe -> subscribeInfoMap[userId] : absent -> register -> add RedisListener -> return subscribeInfoMap[userId].getFlux()
         * req1 : doOnSubscribe -> cnt=1
         * req2 : subscribe -> subscribeInfoMap[userId] : present -> return subscribeInfoMap[userId].getFlux()
         * req1 : doOnCancle -> subscribeInfoMap[userId] : present -> cnt=0 -> subscribeInfoMap[userId]=null -> remove RedisListener
         * req2 : doOnSubscribe -> subscribeInfoMap[userId] : absent -> doNothing
         */
        Mono<RedisUserAlarmSubscribeInfo> infoMono = Mono.fromCallable(() ->
                        subscribeInfoMap.compute(userId, (key, redisUserAlarmSubscribeInfo) -> {
                            //userId에 대해 구독 정보 등록
                            if(redisUserAlarmSubscribeInfo == null) {
                                redisUserAlarmSubscribeInfo = register(key);
                            }
                            //구독자 수 증가
                            redisUserAlarmSubscribeInfo.setSubscribeCnt(redisUserAlarmSubscribeInfo.getSubscribeCnt() + 1);
                            //구독 정보 반환
                            return redisUserAlarmSubscribeInfo;
                        }))
                .subscribeOn(Schedulers.boundedElastic());

        //연결된 Flux 반환
        return infoMono.flatMapMany(RedisUserAlarmSubscribeInfo::getFlux);
    }

    /*
     * userId에 대한 구독 정보를 생성한다.
     * userId에 대한 동기화를 고려해야 한다.
     * 레디스 메세지 리스너를 등록하고 메세지을 읽어 sink 에서 Flux 로 보내도록 만든다.
     * 생성된 구독 정보를 반환한다.
     */
    private RedisUserAlarmSubscribeInfo register(Long userId) {
        // sink 생성, Hot Stream
        Many sink = Sinks.many().multicast().onBackpressureBuffer();
        // sink 에서 데이터를 받고 연결, 구독 관리 설정을 추가한 Flux 생성
        Flux<String> flux = createFlux(userId, sink);
        //redis pub sub 리스너를 추가
        MessageListener redisUserAlarmMessageListener = attachRedisTopicListener(userId, sink);
        //정보 생성해서 반환
        return RedisUserAlarmSubscribeInfo.builder()
                .userId(userId)
                .subscribeCnt(0)
                .sink(sink)
                .flux(flux)
                .messageListener(redisUserAlarmMessageListener)
                .build();
    }

    private Flux<String> createFlux(Long userId, Many<String> sink) {
        // sink 에서 FLux 를 얻어오고 설정 추가. sink.asFlux()는 항상 같은 객체를 반환하고 데이터스트림을 공유.
        // 다만 Flux 는 immutable 하고 retry, doOnError, doOnCancel 호출 시마다 decorator 패턴으로 새로운 Flux 객체가 만들어 진다는 점을 참고.
        // 그리고 Flux 는 가벼운 객체로 비용은 크지 않다.
        Flux<String> messageFlux = sink.asFlux()
                //1번까지는 에러가 나도 재시도
                .retry(1)
                //서버 측 에러
                .doOnError(e -> {
                    log.error("실시간 알람 전송 실패", e);
                })
                //클라이언트가 연결을 종료하면 구독 해제(명시적인 종료 요청에 해당)
                //heartbeat 메세지를 주기적으로 보내서 비정상적인 종료에 의해 남은 연결도 주기적으로 정리.
                .doOnCancel(() -> {
                    // 구독자 수 -1
                    // 이벤트 루프가 잠시 block될수 있으므로 작업 스레드에서 처리
                    Mono.fromRunnable(() -> decreaseSubscriptionCnt(userId))
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe();
                });
        //메세지 Flux 와 heartbeat Flux 를 묶어서 반환
        messageFlux = Flux.merge(messageFlux, heartbeatFlux);
        //sse 준비 완료 즉시 HB 메세지를 한번 방출
        messageFlux = Flux.concat(Mono.just(HEARTBEAT_MESSAGE), messageFlux);
        return messageFlux;
    }

    /*
     * userId에 대해 구독자 수를 감소시킴.
     * userId에 대해 동기화된 처리 수행
     * 구독자 수가 0이 되면 redis pub sub 에 등록된 listener 해제, 연결된 sink 삭제 등 관련 자원 정리
     */
    private void decreaseSubscriptionCnt(Long userId) {
        // 각 userId에 대해 동기화된 작업 수행
        subscribeInfoMap.computeIfPresent(userId, (key, redisUserAlarmSubscribeInfo) -> {
            //구독자 수 1 감소
            redisUserAlarmSubscribeInfo.setSubscribeCnt(redisUserAlarmSubscribeInfo.getSubscribeCnt() - 1);
            // 구독자 수가 0이 되면 리스너를 해제하고 연결된 자원 삭제
            if(redisUserAlarmSubscribeInfo.getSubscribeCnt() == 0) {
                //리스너 해제
                detachRedisTopicListener(redisUserAlarmSubscribeInfo.getMessageListener());
                //map 에서 info 삭제
                redisUserAlarmSubscribeInfo = null;
            }
            return redisUserAlarmSubscribeInfo;
        });
    }

    private RedisUserAlarmMessageListener attachRedisTopicListener(Long userId, Sinks.Many<String> sink) {
        //리스너 생성
        RedisUserAlarmMessageListener userUserAlarmMessageListener = new RedisUserAlarmMessageListener(sink);
        //채널 토픽 생성
        ChannelTopic topic = new ChannelTopic(TOPIC_PREFIX + userId);
        //리스너 등록
        redisMessageListenerContainer.addMessageListener(userUserAlarmMessageListener, topic);
        return userUserAlarmMessageListener;
    }

    private void detachRedisTopicListener(MessageListener listener) {
        // 리스너 해제
        redisMessageListenerContainer.removeMessageListener(listener);
    }
}

