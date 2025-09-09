package com.jzo2o.orders.manager.listener;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSON;
import com.jzo2o.common.constants.MqConstants;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.common.utils.JsonUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.orders.manager.service.impl.OrdersCreateServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接收支付服务队列消息
 */
@Slf4j
@Component
public class TradeStatusListener {

    @Resource
    private OrdersCreateServiceImpl ordersCreateService;

    /**
     * 更新支付结果
     * 支付成功
     *
     * @param msg 消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.Queues.ORDERS_TRADE_UPDATE_STATUS),
            exchange = @Exchange(name = MqConstants.Exchanges.TRADE, type = ExchangeTypes.TOPIC),
            key = MqConstants.RoutingKeys.TRADE_UPDATE_STATUS
    ))
    public void listenTradeUpdatePayStatusMsg(String msg){
        log.info("接收支付消息：" + msg);
        // 转换
        List<TradeStatusMsg> tradeStatusMsgs = JSON.parseArray(msg, TradeStatusMsg.class);
        List<TradeStatusMsg> collect = tradeStatusMsgs.stream().filter(item -> ObjectUtils.equal("jzo2o.orders", item.getProductAppId())
                && ObjectUtils.isNotEmpty(item.getTransactionId())).collect(Collectors.toList());

        if (CollUtil.isEmpty(collect)) {
            return;
        }
        // 更新数据库
        for (TradeStatusMsg tradeStatusMsg : collect) {
            ordersCreateService.paySuccess(tradeStatusMsg);
        }
    }
}
