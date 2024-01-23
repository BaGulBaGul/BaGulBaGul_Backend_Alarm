package com.bagulbagul.bagulbagul.alarm.service.redis;

import com.bagulbagul.bagulbagul.alarm.service.UserAlarmSubscribeManager;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisUserAlarmSubscribeManager implements UserAlarmSubscribeManager {

    //redis 채널명의 prefix
    private final String TOPIC_PREFIX = "alarm_user_";
    //HeartBeat 요청의 간격(초)
    private final int HEARTBEAT_INTERVAL_SECOND = 120;
    //HeartBeat 요청 메세지 내용
    private final String HEARTBEAT_MESSAGE = "HB";
    //레디스 메세지 리스너 관리 컨테이너
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    // user Id -> 등록정보
    private final ConcurrentHashMap<Long, RedisUserAlarmSubscribeInfo> subscribeInfoMap = new ConcurrentHashMap<>();

    //주기적으로 heartbeat 를 보내서 연결이 끊겼는지 확인한다
    //single thread 의 이유
    //1.같은 동작을 반복하므로 캐시 등의 효율성을 위해 한 스레드에서 실행하도록 함.
    //2.연결 정리보다 다른 메세지 전달이 더 중요하기 때문에 다른 Flux 를 방해하지 않기 위해 스레드 제한을 뒀다.
    private final Flux<String> heartbeatFlux = Flux.interval(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECOND), Schedulers.single())
            .map(i -> HEARTBEAT_MESSAGE);


    /*
     * flow : redis_pub/sub -> redis_message_listener -> sink -> flux
     * 1. userId에 연결된 구독 정보가 없으면 등록 후 연결된 Flux 객체 반환
     * 2. Flux를 구독하면 구독자 수 1 증가, 구독 해제하면 구독자 수 1 감소
     * 3. 구독자 수가 1 이상에서 0이 되면 레디스 리스너 해제, 구독 정보 삭제.
     */
    @Override
    public Flux<String> subscribe(final Long userId) {
        // userId에 대한 구독 정보가 없다면 등록. 구독 정보를 가져온다.
        RedisUserAlarmSubscribeInfo info = subscribeInfoMap.computeIfAbsent(userId, (key) -> register(key));
        //연결된 Flux 반환
        return info.getFlux();
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
                //클라이언트가 구독을 시작
                .doOnSubscribe(subscription -> {
                    // 구독자 수 +1
                    increaseSubscriptionCnt(userId);
                })
                //클라이언트가 연결을 종료하면 구독 해제(명시적인 종료 요청에 해당)
                //heartbeat 메세지를 주기적으로 보내서 비정상적인 종료에 의해 남은 연결도 주기적으로 정리.
                .doOnCancel(() -> {
                    // 구독자 수 -1
                    decreaseSubscriptionCnt(userId);
                });
        //메세지 Flux 와 heartbeat Flux 를 묶어서 반환
        return Flux.merge(messageFlux, heartbeatFlux);
    }

    /*
     * userId에 대해 구독자 수를 증가시킴.
     * userId에 대해 동기화된 처리 수행
     */
    private void increaseSubscriptionCnt(Long userId) {
        // 구독자 수 +1
        subscribeInfoMap.computeIfPresent(userId, (key, redisUserAlarmSubscribeInfo) -> {
            redisUserAlarmSubscribeInfo.setSubscribeCnt(redisUserAlarmSubscribeInfo.getSubscribeCnt() + 1);
            return redisUserAlarmSubscribeInfo;
        });
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

//    /*
//     * userId에 대한 구독 정보를 삭제한다.
//     * userId에 대한 동기화를 고려해야 한다.
//     * 레디스 메세지 리스너를 해제하고 info map 에서 구독 정보를 제거한다.
//     */
//    private void unRegister(Long userId) {
//
//    }

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

