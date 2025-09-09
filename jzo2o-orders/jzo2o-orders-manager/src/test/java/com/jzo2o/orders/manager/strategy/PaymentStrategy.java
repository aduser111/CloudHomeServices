package com.jzo2o.orders.manager.strategy;

import java.math.BigDecimal;

/**
 * 策略模式接口
 */
public interface PaymentStrategy {

    void pay(BigDecimal amount);
}
