package com.jzo2o.orders.manager.handler;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jzo2o.api.trade.RefundRecordApi;
import com.jzo2o.api.trade.dto.response.ExecutionResultResDTO;
import com.jzo2o.api.trade.enums.RefundStatusEnum;
import com.jzo2o.common.constants.UserType;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.orders.base.enums.OrderRefundStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.domain.OrdersRefund;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import com.jzo2o.orders.manager.service.IOrdersRefundService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 处理订单定时任务
 */
@Component
@Slf4j
public class OrdersHandler {

    @Resource
    private IOrdersCreateService ordersCreateService;
    @Resource
    private IOrdersManagerService ordersManagerService;
    @Resource
    private IOrdersRefundService ordersRefundService;
    @Resource
    private RefundRecordApi refundRecordApi;
    @Resource
    private OrdersHandler owner;
    @Resource
    private OrdersMapper ordersMapper;

    @XxlJob(value = "cancelOverTimePayOrder")
    public void cancelOverTimePayOrder(){
        // 定时处理超时未支付的订单
        // 100条一处理
        List<Orders> ordersList = ordersCreateService.queryOverTimePayOrdersListByCount(100);
        if (CollUtils.isEmpty(ordersList)){
            log.info("没有超时未支付的订单");
            return;
        }
        for (Orders orders : ordersList) {
            OrderCancelDTO orderCancelDTO = BeanUtils.copyBean(orders, OrderCancelDTO.class);
            orderCancelDTO.setCurrentUserType(UserType.SYSTEM);
            orderCancelDTO.setCancelReason("超时未支付");
            ordersManagerService.cancel(orderCancelDTO);
        }
    }

    /**
     * 申请退款异步任务
     */
    @XxlJob(value = "handleRefundOrders")
    public void handleRefundOrders(){
        List<OrdersRefund> ordersRefunds = ordersRefundService.queryRefundOrderListByCount(100);
        if (CollUtils.isEmpty(ordersRefunds)){
            log.info("没有待退款的订单");
            return;
        }
        // 进行退款
        for (OrdersRefund ordersRefund : ordersRefunds) {
            try {
                requestRefundOrder(ordersRefund);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 请求退款
     * @param ordersRefund
     */
    public void requestRefundOrder(OrdersRefund ordersRefund) {
        ExecutionResultResDTO executionResultResDTO = null;
        try {
            executionResultResDTO = refundRecordApi.refundTrading(ordersRefund.getTradingOrderNo(), ordersRefund.getRealPayAmount());
        }catch (Exception e){
            e.printStackTrace();
        }
        if (executionResultResDTO != null){
            //退款后处理订单相关信息
            owner.refundOrder(ordersRefund, executionResultResDTO);
        }
    }

    /**
     * 更新退款状态
     * @param ordersRefund
     * @param executionResultResDTO
     */
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(OrdersRefund ordersRefund,ExecutionResultResDTO executionResultResDTO){
        //根据响应结果更新退款状态
        int refundStatus = OrderRefundStatusEnum.REFUNDING.getStatus();//退款中
        if (ObjectUtil.equal(RefundStatusEnum.SUCCESS.getCode(), executionResultResDTO.getRefundStatus())) {
            //退款成功
            refundStatus = OrderRefundStatusEnum.REFUND_SUCCESS.getStatus();
        } else if (ObjectUtil.equal(RefundStatusEnum.FAIL.getCode(), executionResultResDTO.getRefundStatus())) {
            //退款失败
            refundStatus = OrderRefundStatusEnum.REFUND_FAIL.getStatus();
        }
        // 还退款中不处理
        if (refundStatus == OrderRefundStatusEnum.REFUNDING.getStatus()){
            return;
        }
        // 更新订单状态
        LambdaUpdateWrapper<Orders> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper
                .eq(Orders::getId,ordersRefund.getId())
                .ne(Orders::getRefundStatus,refundStatus)
                .set(Orders::getRefundId,executionResultResDTO.getRefundId())
                .set(Orders::getRefundNo,executionResultResDTO.getRefundNo())
                .set(Orders::getRefundStatus,refundStatus);
        int update = ordersMapper.update(null, lambdaUpdateWrapper);
        // 删除退款表
        if (update > 0){
            ordersRefundService.removeById(ordersRefund.getId());
        }
    }


    public void requestRefundNewThread(Long orderRefundId){
        new Thread(() -> {
            OrdersRefund ordersRefund = ordersRefundService.getById(orderRefundId);
            if (ObjectUtil.isNotEmpty(ordersRefund)) {
                requestRefundOrder(ordersRefund);
            }
        }).start();
        }
}
