package com.jzo2o.orders.manager.strategy;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;

import javax.validation.Valid;
import java.math.BigDecimal;

public class StrategyTest {

    public static void main(String[] args) {
        ShoppingCart shoppingCart = new ShoppingCart();
        WeiXinPayment weiXinPayment = new WeiXinPayment("好好生活");
        shoppingCart.setPaymentStrategy(weiXinPayment);
        shoppingCart.checkout(BigDecimal.valueOf(100));
        CreditCardPayment creditCardPayment = new CreditCardPayment("111-0000-2222");
        shoppingCart.setPaymentStrategy(creditCardPayment);
        shoppingCart.checkout(BigDecimal.valueOf(200));
    }

}
