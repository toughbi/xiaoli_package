package com.daytrace.ledger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class AiWorkPool {
    static final int MAX_WORKERS = 3;

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            MAX_WORKERS,
            runnable -> new Thread(
                    runnable,
                    "daytrace-ai-worker-" + THREAD_SEQUENCE.incrementAndGet()));

    private AiWorkPool() {}

    static void submit(Runnable task) {
        EXECUTOR.execute(task);
    }
}
