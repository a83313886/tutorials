package com.baeldung.springretry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.listener.RetryListenerSupport;

/**
 * 自己做的{@code RetryListener}实现，用来观察相关方法的调用情况
 */
public class DefaultListenerSupport extends RetryListenerSupport {

    private static final Logger logger = LoggerFactory.getLogger(DefaultListenerSupport.class);

    // 整个retry结束时被调用
    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        logger.info("onClose");
        super.close(context, callback, throwable);
    }

    // 目标方法出现异常时被调用
    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        logger.info("onError");
        super.onError(context, callback, throwable);
    }

    // 整个retry开始时被调用
    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        logger.info("onOpen");
        return super.open(context, callback);
    }

}
