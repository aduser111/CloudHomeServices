package com.jzo2o.orders.manager.strategy;

import java.math.BigDecimal;

public class WeiXinPayment implements PaymentStrategy{
    private String accound;

    public WeiXinPayment(String accound){
        this.accound = accound;
    }
    @Override
    public void pay(BigDecimal amount) {
        System.out.println("微信用户："+ accound + "支付金额：" + amount);
    }
}
