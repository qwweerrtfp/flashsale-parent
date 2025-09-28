package com.ye94z.order.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

// package com.ye94z.order.sse;
@Component
public class SseHub {
    private final ConcurrentMap<Long, CopyOnWriteArraySet<SseEmitter>> subs = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long orderId, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        subs.computeIfAbsent(orderId, k -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(orderId, emitter));
        emitter.onTimeout(() -> remove(orderId, emitter));
        emitter.onError(e -> remove(orderId, emitter));
        return emitter;
    }
    public void send(Long orderId, String event, Object data) {
        var set = subs.get(orderId);
        if (set == null) return;
        for (SseEmitter s : set) {
            try {
                s.send(SseEmitter.event().name(event).data(data));
            } catch (Exception e) {
                remove(orderId, s);
            }
        }
    }
    public void complete(Long orderId) {
        var set = subs.remove(orderId);
        if (set != null) set.forEach(SseEmitter::complete);
    }
    private void remove(Long orderId, SseEmitter s) {
        var set = subs.get(orderId);
        if (set != null) { set.remove(s); if (set.isEmpty()) subs.remove(orderId); }
    }
}