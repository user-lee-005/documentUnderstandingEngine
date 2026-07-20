package com.pranicdoc.docengine.ocr;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pools expensive, non-thread-safe native-backed resources (Tesseract instances, OpenCV Mats)
 * so page-level parallelism doesn't contend on or corrupt a single shared instance.
 * Generic over the resource type — Roadmap Phase 4 is wiring this up to real Tesseract/OpenCV
 * construction, not the pooling mechanics themselves.
 */
public class NativeResourcePool<T> implements AutoCloseable {

    private final BlockingQueue<T> pool;
    private final Consumer<T> closer;

    public NativeResourcePool(int size, Supplier<T> factory, Consumer<T> closer) {
        this.closer = closer;
        this.pool = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            pool.add(factory.get());
        }
    }

    public T borrow() throws InterruptedException {
        return pool.take();
    }

    public void release(T resource) {
        pool.offer(resource);
    }

    @Override
    public void close() {
        pool.forEach(closer);
    }
}
