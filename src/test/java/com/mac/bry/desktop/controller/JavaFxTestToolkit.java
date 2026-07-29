package com.mac.bry.desktop.controller;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;

/**
 * Jednorazowy start toolkitu JavaFX dla testów.
 * <p>
 * Toolkitu nie da się w jednej JVM wystartować dwa razy, a {@code Platform.exit()}
 * zamyka go nieodwracalnie — klasa testowa wołająca go w {@code @AfterAll}
 * wywracała każdą kolejną klasę testów JavaFX komunikatem
 * „Platform.exit has been called”. Dlatego zamknięcie jest tu odłożone do
 * zamknięcia JVM: wątek FX (non-daemon) nadal nie blokuje forka Surefire, ale
 * kolejne klasy testowe mogą z toolkitu korzystać.
 */
final class JavaFxTestToolkit {

    private static boolean started;

    private JavaFxTestToolkit() {
    }

    static synchronized void startOnce() throws InterruptedException {
        if (started) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(latch::countDown);
        latch.await();
        Platform.setImplicitExit(false);
        Runtime.getRuntime().addShutdownHook(new Thread(Platform::exit, "javafx-test-shutdown"));
        started = true;
    }
}