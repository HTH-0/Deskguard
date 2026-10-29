package com.promptgateway.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 시험 중에 남은 로그를 메모리에 모아서 확인하게 해 주는 도구. try-with-resources로 쓰면 시험이 끝날 때 떼어진다.
 *
 *   try (LogCapture logs = LogCapture.of(GatewayLogger.class)) { ... logs.messages() ... }
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Logger logger) {
        this.logger = logger;
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture of(Class<?> type) {
        return new LogCapture((Logger) LoggerFactory.getLogger(type));
    }

    /** 모든 로거의 로그를 모은다. */
    public static LogCapture root() {
        return new LogCapture((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME));
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    public List<String> messages() {
        return events().stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** 지정한 수준 이상인 로그의 메시지와 예외 내용(메시지, 스택)을 모두 이어 붙인 글. 비밀 값이 없는지 확인할 때 쓴다. */
    public String fullTextAtLeast(Level level) {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent e : events()) {
            if (e.getLevel().isGreaterOrEqual(level)) {
                sb.append(e.getFormattedMessage()).append('\n');
                appendThrowable(sb, e.getThrowableProxy());
            }
        }
        return sb.toString();
    }

    private static void appendThrowable(StringBuilder sb, IThrowableProxy t) {
        List<IThrowableProxy> seen = new ArrayList<>();
        while (t != null && !seen.contains(t)) {
            seen.add(t);
            sb.append(t.getClassName()).append(": ").append(t.getMessage()).append('\n');
            for (StackTraceElementProxy s : t.getStackTraceElementProxyArray()) {
                sb.append(s.getSTEAsString()).append('\n');
            }
            t = t.getCause();
        }
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }
}
