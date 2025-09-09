package com.jzo2o.orders.manager.strategy;

import java.math.BigDecimal;

public class ShoppingCart {
    private PaymentStrategy paymentStrategy;

    public void setPaymentStrategy(PaymentStrategy paymentStrategy){
        this.paymentStrategy = paymentStrategy;
    }

    public void checkout(BigDecimal amount){
        paymentStrategy.pay(amount);
    }
}
