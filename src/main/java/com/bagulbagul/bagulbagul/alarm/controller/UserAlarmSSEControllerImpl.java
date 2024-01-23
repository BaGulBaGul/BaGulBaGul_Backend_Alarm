package com.bagulbagul.bagulbagul.alarm.controller;

import com.bagulbagul.bagulbagul.alarm.service.UserAlarmSubscribeManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Flux;

@Controller
@RequestMapping("/alarm")
@RequiredArgsConstructor
public class UserAlarmSSEControllerImpl implements UserAlarmSSEController {

    private final UserAlarmSubscribeManager userAlarmSubscribeManager;

    @Override
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> subscribe(
            @AuthenticationPrincipal Long userId
    ) {
        // 구독하고 연결된 flux 를 얻어옴.
        return userAlarmSubscribeManager.subscribe(userId);
    }
}
