package com.ptcrys.fpsmatch.common.client.minimap.editor;

import java.util.Objects;
import java.util.concurrent.Future;

public final class EditorTaskHandle {
    private final String taskId;
    private final Future<?> future;

    EditorTaskHandle(String taskId, Future<?> future) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.future = Objects.requireNonNull(future, "future");
    }

    public String taskId() {
        return taskId;
    }

    public void cancel() {
        future.cancel(true);
    }

    public boolean isDone() {
        return future.isDone();
    }
}