package com.sunyard.llm.mas.util;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;

/**
 * 将 MyBatis-Plus 阻塞调用调度到 boundedElastic 线程池，保持对外 Mono 接口不变。
 * Netty 事件循环线程不阻塞，缓存写入仍可 fire-and-forget。
 */
public final class ReactiveDbAdapter {

    private static final Scheduler DB_SCHEDULER = Schedulers.boundedElastic();

    private ReactiveDbAdapter() {
    }

    /**
     * 包装阻塞调用为 Mono，调度到 boundedElastic。
     *
     * @param blockingCall 阻塞式数据库操作
     * @param <T>          返回值类型
     * @return 响应式 Mono
     */
    public static <T> Mono<T> mono(Callable<T> blockingCall) {
        return Mono.fromCallable(blockingCall).subscribeOn(DB_SCHEDULER);
    }

    /**
     * 包装无返回值的阻塞调用为 Mono<Void>。
     *
     * @param blockingCall 阻塞式数据库操作
     * @return 响应式 Mono<Void>
     */
    public static Mono<Void> monoVoid(Runnable blockingCall) {
        return Mono.<Void>fromRunnable(blockingCall).subscribeOn(DB_SCHEDULER);
    }
}
