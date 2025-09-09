package com.jzo2o.orders.manager.strategy;

import java.math.BigDecimal;

public class CreditCardPayment implements PaymentStrategy{

    private String cardNumber;

    public CreditCardPayment(String cardNumber){
        this.cardNumber = cardNumber;
    }
    @Override
    public void pay(BigDecimal amount) {
        System.out.println("银行账户："+ cardNumber + "支付金额：" + amount);
    }
}
