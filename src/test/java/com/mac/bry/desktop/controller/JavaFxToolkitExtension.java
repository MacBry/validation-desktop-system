package com.mac.bry.desktop.controller;

import javafx.application.Platform;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.CountDownLatch;

/**
 * Startuje toolkit JavaFX raz na przebieg testów i zamyka go dopiero po
 * wszystkich klasach.
 * <p>
 * Dwa ograniczenia trzeba pogodzić naraz. Toolkitu nie da się w jednej JVM
 * uruchomić drugi raz, więc {@code Platform.exit()} w {@code @AfterAll}
 * pojedynczej klasy wywracał każdą kolejną klasę testów JavaFX komunikatem
 * „Platform.exit has been called”. Jednocześnie wątek FX jest wątkiem
 * non-daemon, więc pozostawienie go żywego opóźniało zamknięcie forka
 * Surefire o 30 sekund („Surefire is going to kill self fork JVM”).
 * <p>
 * Rozwiązaniem jest zasób rejestrowany w korzeniu magazynu JUnit: zamykany
 * jest po zakończeniu całego przebiegu, a więc po ostatniej klasie testowej
 * i jeszcze przed zamknięciem JVM przez Surefire.
 */
public class JavaFxToolkitExtension implements BeforeAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(JavaFxToolkitExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot()
                .getStore(NAMESPACE)
                .getOrComputeIfAbsent("javafx-toolkit", key -> new ToolkitResource(), ToolkitResource.class);
    }

    static final class ToolkitResource implements ExtensionContext.Store.CloseableResource {

        ToolkitResource() {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Przerwano start toolkitu JavaFX", e);
            }
            Platform.setImplicitExit(false);
        }

        @Override
        public void close() {
            Platform.exit();
        }
    }
}