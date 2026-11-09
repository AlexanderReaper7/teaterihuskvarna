package se.teaterihuskvarna;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/// Counts the tasks on Spring Boot's `applicationTaskExecutor` that are queued
/// or running, so a test can wait until [se.teaterihuskvarna.login.Background]
/// has finished what a request handed it, instead of sleeping for a fixed time.
///
/// The count goes up in the executor's `execute`, on the thread that queues the
/// task, and down when the task ends. A request queues its work before its
/// response returns, so once a test has the response, a count of zero means
/// that work is done.
///
/// A customizer rather than a `TaskDecorator` bean, because Boot 4.1 applies a
/// `TaskDecorator` bean to the task scheduler as well, and a `@Scheduled` job
/// is decorated once and run many times, which would leave the count wrong.
@TestConfiguration(proxyBeanMethods = false)
public class BackgroundTasks {

    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicBoolean counting = new AtomicBoolean();

    /// @return the customizer Boot applies to the `applicationTaskExecutor` it builds
    @Bean
    ThreadPoolTaskExecutorCustomizer countBackgroundTasks() {
        return executor -> {
            counting.set(true);
            executor.setTaskDecorator(task -> {
                pending.incrementAndGet();
                return () -> {
                    try {
                        task.run();
                    } finally {
                        pending.decrementAndGet();
                    }
                };
            });
        };
    }

    /// @return whether Boot built the executor with the customizer, so that [#pending] means anything
    boolean counting() {
        return counting.get();
    }

    /// @return how many tasks are queued or running
    int pending() {
        return pending.get();
    }
}
