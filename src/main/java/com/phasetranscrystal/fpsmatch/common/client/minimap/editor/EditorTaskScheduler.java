package com.phasetranscrystal.fpsmatch.common.client.minimap.editor;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class EditorTaskScheduler implements AutoCloseable {
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "fpsmatch-minimap-editor-task");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Future<?>> running = new ConcurrentHashMap<>();

    public EditorTaskHandle schedule(String taskId, Runnable work) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(work, "work");
        Future<?> existing = running.get(taskId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(true);
        }
        Future<?> future = executor.submit(() -> {
            try {
                work.run();
            } finally {
                running.remove(taskId);
            }
        });
        running.put(taskId, future);
        return new EditorTaskHandle(taskId, future);
    }

    public boolean isRunning(String taskId) {
        Future<?> future = running.get(taskId);
        return future != null && !future.isDone();
    }

    public void cancelAll() {
        for (Future<?> future : running.values()) {
            future.cancel(true);
        }
        running.clear();
    }

    public void awaitIdle(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
        while (!running.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void close() {
        cancelAll();
        executor.shutdownNow();
        try {
            executor.awaitTermination(250L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}