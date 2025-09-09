package com.jzo2o.orders.manager.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.db.DbRuntimeException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.ServeApi;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.api.market.dto.request.CouponUseReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.market.dto.response.CouponUseResDTO;
import com.jzo2o.api.trade.NativePayApi;
import com.jzo2o.api.trade.TradingApi;
import com.jzo2o.api.trade.dto.request.NativePayReqDTO;
import com.jzo2o.api.trade.dto.response.NativePayResDTO;
import com.jzo2o.api.trade.dto.response.TradingResDTO;
import com.jzo2o.api.trade.enums.PayChannelEnum;
import com.jzo2o.api.trade.enums.TradingStateEnum;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.DateUtils;
import com.jzo2o.common.utils.NumberUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.orders.base.config.OrderStateMachine;
import com.jzo2o.orders.base.constants.RedisConstants;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusChangeEventEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.enums.ServeStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.manager.model.dto.request.OrdersPayReqDTO;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.orders.manager.porperties.TradeProperties;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.orders.manager.service.client.CustomerClient;
import com.jzo2o.orders.manager.service.client.FoundationsClient;
import com.jzo2o.orders.manager.service.client.MarketClient;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 下单服务类
 * </p>
 *
 * @author wh
 * @since 2025-04-13
 */
@Slf4j
@Service
public class OrdersCreateServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersCreateService {

    @Resource
    private CustomerClient customerClient;
    @Resource
    private FoundationsClient foundationsClient;
    @Resource
    private MarketClient marketClient;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private OrdersCreateServiceImpl owner;
    @Resource
    private TradeProperties tradeProperties;
    @Resource
    private NativePayApi nativePayApi;
    @Resource
    private TradingApi tradingApi;
    @Resource
    private OrderStateMachine orderStateMachine;

    private Long generateOrderId() {
        //通过Redis自增序列得到序号
        Long id = redisTemplate.opsForValue().increment(RedisConstants.Lock.ORDERS_SHARD_KEY_ID_GENERATOR, 1);
        //生成订单号   2位年+2位月+2位日+13位序号
        long orderId = DateUtils.getFormatDate(LocalDateTime.now(), "yyMMdd") * 10000000000000L + id;
        return orderId;
    }

    /**
     * 用户下单
     * @param placeOrderReqDTO
     * @return
     */
    public PlaceOrderResDTO place(@RequestBody PlaceOrderReqDTO placeOrderReqDTO){

        //下单人信息，获取地址簿，调用jzo2o-customer服务获取
        AddressBookResDTO addressBookDTO = customerClient.getDetail(placeOrderReqDTO.getAddressBookId());
        //服务相关信息,调用jzo2o-foundations获取
        ServeAggregationResDTO serveDTO = foundationsClient.getServeDetail(placeOrderReqDTO.getServeId());
        if (BeanUtils.isEmpty(addressBookDTO) || BeanUtils.isEmpty(serveDTO)){
            throw new CommonException("信息获取错误！");
        }
        //生成订单号
        Long orderId = generateOrderId();
        //计算价格
        BigDecimal totalAmount = serveDTO.getPrice().multiply(new BigDecimal(placeOrderReqDTO.getPurNum()));
        //组装订单信息，插入数据库订单表，订单状态为待支付
        Orders orders = new Orders();
        orders.setId(orderId);
        // 订单用户id
        orders.setUserId(UserContext.currentUserId());
        // 服务相关信息
        orders.setServeTypeId(serveDTO.getServeTypeId());
        orders.setServeTypeName(serveDTO.getServeTypeName());
        orders.setServeItemId(serveDTO.getServeItemId());
        orders.setServeItemName(serveDTO.getServeItemName());
        orders.setServeItemImg(serveDTO.getServeItemImg());
        orders.setUnit(serveDTO.getUnit());
        orders.setServeId(placeOrderReqDTO.getServeId());
        // 状态信息
        orders.setOrdersStatus(OrderStatusEnum.NO_PAY.getStatus());
        orders.setPayStatus(OrderPayStatusEnum.NO_PAY.getStatus());
        // 订单价格信息
        orders.setPrice(serveDTO.getPrice());
        orders.setPurNum(placeOrderReqDTO.getPurNum());
        orders.setTotalAmount(totalAmount);
        orders.setDiscountAmount(BigDecimal.ZERO);
        orders.setRealPayAmount(NumberUtils.sub(orders.getTotalAmount(), orders.getDiscountAmount()));
        // 地址信息
        orders.setCityCode(serveDTO.getCityCode());
        String address = new StringBuffer()
                .append(addressBookDTO.getProvince())
                .append(addressBookDTO.getCity())
                .append(addressBookDTO.getCounty())
                .append(addressBookDTO.getAddress())
                .toString();
        orders.setServeAddress(address);
        orders.setContactsPhone(addressBookDTO.getPhone());
        orders.setContactsName(addressBookDTO.getName());
        orders.setServeStartTime(placeOrderReqDTO.getServeStartTime());
        orders.setLon(addressBookDTO.getLon());
        orders.setLat(addressBookDTO.getLat());
        //排序字段,根据服务开始时间转为毫秒时间戳+订单后5位
        long sortBy = DateUtils.toEpochMilli(orders.getServeStartTime()) + orders.getId() % 100000;
        orders.setSortBy(sortBy);
        if (ObjectUtils.isNull(placeOrderReqDTO.getCouponId())){
            // 无优惠劵下单
            owner.add(orders);
        }else {
            // 有优惠劵下单
            owner.addWithCoupon(orders,placeOrderReqDTO.getCouponId());
        }
        PlaceOrderResDTO placeOrderResDTO = new PlaceOrderResDTO();
        placeOrderResDTO.setId(orderId);
        return placeOrderResDTO;
    };

    /**
     * 保存订单表
     * @param orders
     */
    @Transactional(rollbackFor = Exception.class)
    public void add(Orders orders){
        boolean save = this.save(orders);
        if (!save) {
            throw new DbRuntimeException("下单失败");
        }
        //构建快照对象
        OrderSnapshotDTO orderSnapshotDTO = BeanUtil.toBean(baseMapper.selectById(orders.getId()), OrderSnapshotDTO.class);
        //状态机启动
        orderStateMachine.start(orders.getUserId(),String.valueOf(orders.getId()),orderSnapshotDTO);
    }

    /**
     * 保存订单表（使用了优惠劵）
     * @param orders
     * @param couponId
     */
    @GlobalTransactional
    public void addWithCoupon(Orders orders ,Long couponId){
        // 核销优惠劵
        CouponUseReqDTO couponUseReqDTO = new CouponUseReqDTO();
        couponUseReqDTO.setId(couponId);
        couponUseReqDTO.setOrdersId(orders.getId());
        couponUseReqDTO.setTotalAmount(orders.getTotalAmount());
        CouponUseResDTO couponUseResDTO = marketClient.use(couponUseReqDTO);
        // 设置优惠后的金额
        orders.setDiscountAmount(couponUseResDTO.getDiscountAmount());
        orders.setRealPayAmount(NumberUtils.sub(orders.getTotalAmount(), orders.getDiscountAmount()));
        boolean save = this.save(orders);
        if (!save) {
            throw new DbRuntimeException("下单失败");
        }
//        //构建快照对象
//        OrderSnapshotDTO orderSnapshotDTO = BeanUtil.toBean(baseMapper.selectById(orders.getId()), OrderSnapshotDTO.class);
//        //状态机启动
//        orderStateMachine.start(orders.getUserId(),String.valueOf(orders.getId()),orderSnapshotDTO);
    }





    /**
     * 订单支付
     * @param id
     * @param ordersPayReqDTO
     * @return
     */
    @Override
    public OrdersPayResDTO pay(Long id, OrdersPayReqDTO ordersPayReqDTO) {
        Orders orders = this.getById(id);
        // 进行校验
        if (ObjectUtils.isEmpty(orders)){
            throw new CommonException("订单不存在");
        }
        if (ObjectUtils.equal(orders.getPayStatus(),OrderPayStatusEnum.PAY_SUCCESS.getStatus())){
            if (ObjectUtils.isEmpty(orders.getTransactionId())){
                throw new CommonException("数据错误id: " + id);
            }else {
                // 已经正确完成交易
                OrdersPayResDTO ordersPayResDTO = BeanUtils.toBean(orders, OrdersPayResDTO.class);
                ordersPayResDTO.setProductOrderNo(id);
                return ordersPayResDTO;
            }
        }else {
            //生成二维码
            NativePayResDTO nativePayResDTO = generateQrCode(orders, ordersPayReqDTO.getTradingChannel());
            OrdersPayResDTO ordersPayResDTO = BeanUtil.toBean(nativePayResDTO, OrdersPayResDTO.class);
            return ordersPayResDTO;
        }
    }


    /**
     * 获取二维码
     * @param orders
     * @param tradingChannel
     * @return
     */
    private NativePayResDTO generateQrCode(Orders orders, PayChannelEnum tradingChannel) {
        NativePayReqDTO nativePayReqDTO = new NativePayReqDTO();
        // 获取交易商家id
        if (ObjectUtils.equal(PayChannelEnum.WECHAT_PAY,tradingChannel)){
            nativePayReqDTO.setEnterpriseId(tradeProperties.getWechatEnterpriseId());
        }else {
            nativePayReqDTO.setEnterpriseId(tradeProperties.getAliEnterpriseId());
        }
        // 是否切换支付方式
        if (ObjectUtil.isNotEmpty(orders.getTradingChannel())
                && ObjectUtils.notEqual(orders.getTradingChannel(),tradingChannel)){
            nativePayReqDTO.setChangeChannel(true);
        }
        // 业务标识
        nativePayReqDTO.setProductAppId("jzo2o.orders");
        // 业务系统订单号
        nativePayReqDTO.setProductOrderNo(orders.getId());
        // 支付渠道
        nativePayReqDTO.setTradingChannel(tradingChannel);
        // 支付金额
        nativePayReqDTO.setTradingAmount(new BigDecimal("0.01")); // todo 开发金额设置
        // 支付备注
        nativePayReqDTO.setMemo(orders.getServeItemName());
        // 调用支付服务进行支付
        NativePayResDTO downLineTrading = nativePayApi.createDownLineTrading(nativePayReqDTO);
        if (BeanUtils.isNotEmpty(downLineTrading)){
            log.info("订单:{}请求支付,生成二维码:{}",orders.getId(),downLineTrading.toString());
            boolean update = lambdaUpdate()
                    .eq(Orders::getId, downLineTrading.getProductOrderNo())
                    .set(Orders::getTradingOrderNo, downLineTrading.getTradingOrderNo())
                    .set(Orders::getTradingChannel, downLineTrading.getTradingChannel())
                    .update();
            if (!update){
                throw new CommonException("交易错误");
            }
        }
        return downLineTrading;
    }

    /**
     * 请求支付服务查询支付结果
     * @param id 订单id
     * @return
     */
    @Override
    public OrdersPayResDTO getPayResultFromTradServer(Long id) {
        Orders orders = this.getById(id);
        if (ObjectUtils.isEmpty(orders)){
            throw new CommonException("订单不存在，修改错误");
        }
        // 交易还没有确认成功
        if (ObjectUtils.notEqual(orders.getPayStatus(),OrderPayStatusEnum.PAY_SUCCESS.getStatus())
                && ObjectUtils.isNotEmpty(orders.getTradingOrderNo())){
            // 远程调用，查看交易结果
            TradingResDTO tradingResDTO = tradingApi.findTradResultByTradingOrderNo(orders.getTradingOrderNo());
            // 如何支付成功，更新交易订单
            if (ObjectUtils.isNotEmpty(tradingResDTO) && ObjectUtils.equal(tradingResDTO.getTradingState(), TradingStateEnum.YJS)){
                // 更新订单
                TradeStatusMsg tradeStatusMsg = TradeStatusMsg.builder()
                        .productOrderNo(orders.getId())
                        .tradingChannel(tradingResDTO.getTradingChannel())
                        .statusCode(TradingStateEnum.YJS.getCode())
                        .tradingOrderNo(tradingResDTO.getTradingOrderNo())
                        .transactionId(tradingResDTO.getTransactionId())
                        .build();
                owner.paySuccess(tradeStatusMsg);
                // 成功，返回数据
                OrdersPayResDTO ordersPayResDTO = BeanUtils.toBean(tradeStatusMsg, OrdersPayResDTO.class);
                ordersPayResDTO.setPayStatus(OrderPayStatusEnum.PAY_SUCCESS.getStatus());
                return ordersPayResDTO;
            }
        }
        // 直接返回数据库数据
        OrdersPayResDTO ordersPayResDTO = BeanUtils.toBean(orders,OrdersPayResDTO.class);
        ordersPayResDTO.setProductOrderNo(orders.getId());
        return ordersPayResDTO;
    }

    /**
     * 支付成功，修改订单状态
     * @param tradeStatusMsg 交易状态消息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(TradeStatusMsg tradeStatusMsg) {
        Orders orders = this.getById(tradeStatusMsg.getProductOrderNo());
        // 校验
        if (ObjectUtils.isEmpty(orders)){
            throw new CommonException("订单不存在，修改错误");
        }
        if (ObjectUtils.equal(orders.getPayStatus(),OrderPayStatusEnum.PAY_SUCCESS.getStatus())){
            log.info("已经支付过了");
            return;
        }
        if (ObjectUtils.notEqual(orders.getOrdersStatus(),OrderStatusEnum.NO_PAY.getStatus())){
            throw new CommonException("该状态不可修改为刚支付成功");
        }
        // 第三方状态校验
        if (ObjectUtils.isEmpty(tradeStatusMsg.getTransactionId())){
            throw new CommonException("无第三方支付交易单号");
        }
//        boolean update = lambdaUpdate()
//                .eq(Orders::getId, tradeStatusMsg.getProductOrderNo())
//                .set(Orders::getPayTime, LocalDateTime.now())
//                .set(Orders::getOrdersStatus, OrderStatusEnum.DISPATCHING.getStatus())
//                .set(Orders::getPayStatus, OrderPayStatusEnum.PAY_SUCCESS.getStatus())
//                .set(Orders::getTradingOrderNo, tradeStatusMsg.getTradingOrderNo()) // 交易服务单号
//                .set(Orders::getTradingChannel, tradeStatusMsg.getTradingChannel()) // 交易方式
//                .set(Orders::getTransactionId, tradeStatusMsg.getTransactionId()) // 第三方交易单号
//                .update();
//        if (!update){
//            throw new CommonException("交易状态更改失败");
//        }
        // 修改订单状态和支付状态
        OrderSnapshotDTO orderSnapshotDTO = OrderSnapshotDTO.builder()
                .payTime(LocalDateTime.now())
                .tradingOrderNo(tradeStatusMsg.getTradingOrderNo())
                .tradingChannel(tradeStatusMsg.getTradingChannel())
                .thirdOrderId(tradeStatusMsg.getTransactionId())
                .build();
        orderStateMachine.changeStatus(orders.getUserId(), String.valueOf(orders.getId()), OrderStatusChangeEventEnum.PAYED, orderSnapshotDTO);
    }

    /**
     * 查询超时未支付的订单
     * @param count
     * @return
     */
    @Override
    public List<Orders> queryOverTimePayOrdersListByCount(Integer count) {
        List<Orders> list = lambdaQuery()
                .eq(Orders::getOrdersStatus, OrderStatusEnum.NO_PAY.getStatus())
                .lt(Orders::getCreateTime, LocalDateTime.now().minusMinutes(15))
                .last("limit " + count)
                .list();
        return list;
    }

    /**
     * 订单服务查询可用优惠劵
     * @param serveId
     * @param purNum
     * @return
     */
    @Override
    public List<AvailableCouponsResDTO> getAvailableCoupons(Long serveId, Integer purNum) {
        // 计算订单价格
        ServeAggregationResDTO serveAggregationResDTO = foundationsClient.getServeDetail(serveId);
        if (ObjectUtils.isNull(serveAggregationResDTO) || serveAggregationResDTO.getSaleStatus() != ServeStatusEnum.SERVING.getStatus()){
            throw new CommonException("商品服务信息错误");
        }
        // 单价
        BigDecimal price = serveAggregationResDTO.getPrice();
        BigDecimal totalAmount = price.multiply(new BigDecimal(purNum));
        // 调用marketApi查询可用优惠劵
        List<AvailableCouponsResDTO> list = marketClient.getAvailable(totalAmount);
        return list;
    }
}
