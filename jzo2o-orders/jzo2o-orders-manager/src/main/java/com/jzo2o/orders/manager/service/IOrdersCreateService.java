package com.jzo2o.orders.manager.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.orders.dto.response.OrderResDTO;
import com.jzo2o.api.orders.dto.response.OrderSimpleResDTO;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.model.dto.request.OrderPageQueryReqDTO;
import com.jzo2o.orders.manager.model.dto.request.OrdersPayReqDTO;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OperationOrdersDetailResDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import io.swagger.models.auth.In;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * <p>
 * 下单服务类
 * </p>
 *
 * @author wenhao
 */
public interface IOrdersCreateService extends IService<Orders> {

    /**
     * 用户下单
     * @param placeOrderReqDTO
     * @return
     */
    public PlaceOrderResDTO place(@RequestBody PlaceOrderReqDTO placeOrderReqDTO);

    /**
     * 订单支付
     * @param id
     * @param ordersPayReqDTO
     * @return
     */
    public OrdersPayResDTO pay(Long id,OrdersPayReqDTO ordersPayReqDTO);

    /**
     * 请求支付服务查询支付结果
     *
     * @param id 订单id
     * @return 订单支付结果
     */
    OrdersPayResDTO getPayResultFromTradServer(Long id);
    /**
     * 支付成功， 更新数据库的订单表及其他信息
     *
     * @param tradeStatusMsg 交易状态消息
     */
    void paySuccess(TradeStatusMsg tradeStatusMsg);

    /**
     * 查询超时未支付的订单
     * @param count
     * @return
     */
    List<Orders> queryOverTimePayOrdersListByCount(Integer count);

    /**
     * 订单服务查询可用优惠劵
     * @param serveId
     * @param purNum
     * @return
     */
    List<AvailableCouponsResDTO> getAvailableCoupons(Long serveId, Integer purNum);
}
