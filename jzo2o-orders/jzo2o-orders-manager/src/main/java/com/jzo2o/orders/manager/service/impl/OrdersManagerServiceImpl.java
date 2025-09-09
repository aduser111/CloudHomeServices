package com.jzo2o.orders.manager.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.api.orders.dto.response.OrderResDTO;
import com.jzo2o.api.orders.dto.response.OrderSimpleResDTO;
import com.jzo2o.common.constants.UserType;
import com.jzo2o.common.enums.EnableStatusEnum;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.mysql.utils.PageHelperUtils;
import com.jzo2o.orders.base.config.OrderStateMachine;
import com.jzo2o.orders.base.constants.RedisConstants;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderRefundStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusChangeEventEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.domain.OrdersCanceled;
import com.jzo2o.orders.base.model.domain.OrdersRefund;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.base.model.dto.OrderUpdateStatusDTO;
import com.jzo2o.orders.base.service.IOrdersCommonService;
import com.jzo2o.orders.manager.handler.OrdersHandler;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.model.dto.request.OrderPageQueryReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.service.IOrdersCanceledService;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import com.jzo2o.orders.manager.service.IOrdersRefundService;
import com.jzo2o.orders.manager.service.client.MarketClient;
import com.jzo2o.redis.helper.CacheHelper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jzo2o.orders.base.constants.FieldConstants.SORT_BY;
import static com.jzo2o.orders.base.constants.RedisConstants.RedisKey.ORDERS;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author itcast
 * @since 2023-07-10
 */
@Slf4j
@Service
public class OrdersManagerServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersManagerService {

    @Resource
    private OrdersManagerServiceImpl owner;
    @Resource
    private IOrdersCanceledService ordersCanceledService;
    @Resource
    private IOrdersCommonService ordersCommonService;
    @Resource
    private IOrdersCreateService ordersCreateService;
    @Resource
    private IOrdersRefundService ordersRefundService;
    @Resource
    private OrdersHandler ordersHandler;
    @Resource
    private OrderStateMachine orderStateMachine;
    @Resource
    private CacheHelper cacheHelper;
    @Resource
    private MarketClient marketClient;

    @Override
    public List<Orders> batchQuery(List<Long> ids) {
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery().in(Orders::getId, ids).ge(Orders::getUserId, 0);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public Orders queryById(Long id) {
        return baseMapper.selectById(id);
    }

    /**
     * 滚动分页查询
     *
     * @param currentUserId 当前用户id
     * @param ordersStatus  订单状态，0：待支付，100：派单中，200：待服务，300：服务中，400：待评价，500：订单完成，600：已取消，700：已关闭
     * @param sortBy        排序字段
     * @return 订单列表
     */
    @Override
    public List<OrderSimpleResDTO> consumerQueryList(Long currentUserId, Integer ordersStatus, Long sortBy) {
        //1.构件查询条件
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery()
                .eq(ObjectUtils.isNotNull(ordersStatus), Orders::getOrdersStatus, ordersStatus)
                .lt(ObjectUtils.isNotNull(sortBy), Orders::getSortBy, sortBy)
                .eq(Orders::getUserId, currentUserId)
                .eq(Orders::getDisplay, EnableStatusEnum.ENABLE.getStatus())
                .select(Orders::getId);
        Page<Orders> queryPage = new Page<>();
        queryPage.addOrder(OrderItem.desc(SORT_BY));
        queryPage.setSearchCount(false);

        //2.查询订单列表
        Page<Orders> ordersPage = baseMapper.selectPage(queryPage, queryWrapper);
        if (CollUtils.isEmpty(ordersPage.getRecords())){
            return CollUtils.emptyList();
        }
        //获取id
        List<Orders> records = ordersPage.getRecords();
        List<Long> ids = new ArrayList<>();
        records.forEach(item -> ids.add(item.getId()));
        // 通过缓存查询，没有的数据再查询数据库
        // 参数： String dataType, List<K> objectIds,
        // BatchDataQueryExecutor<K, T> batchDataQueryExecutor, 函数式方法(Map<K, T> execute(List<K> objectIds, Class<T> clazz);)
        // Class<T> clazz, Long ttl
        String dateType = String.format(ORDERS, UserContext.currentUserId());
        List<OrderSimpleResDTO> orderSimpleResDTOS = cacheHelper.<Long, OrderSimpleResDTO>batchGet(dateType, ids, (noIds, resultClass) -> {
            List<Orders> orders = batchQuery(noIds);
            // 防止缓存穿透缓存空数据
            if (CollUtils.isEmpty(orders)) {
                return CollUtils.emptyMap();
            }
            Map<Long, OrderSimpleResDTO> collect = orders.stream().collect(Collectors.toMap(Orders::getId, item -> BeanUtils.copyBean(item, OrderSimpleResDTO.class)));
            return collect;
        }, OrderSimpleResDTO.class, 600L);
//        List<Orders> orders = batchQuery(ids);
//        List<OrderSimpleResDTO> orderSimpleResDTOS = BeanUtil.copyToList(orders, OrderSimpleResDTO.class);
        return orderSimpleResDTOS;

    }
    /**
     * 根据订单id查询
     *
     * @param id 订单id
     * @return 订单详情
     */
    @Override
    public OrderResDTO getDetail(Long id) {
        Orders orders = queryById(id);
        orders = canalIfPayOvertime(orders);
        OrderResDTO orderResDTO = BeanUtil.toBean(orders, OrderResDTO.class);
        return orderResDTO;
    }

    /**
     * 懒加载方式取消超时订单
     * @param orders
     * @return
     */
    @GlobalTransactional
    private Orders canalIfPayOvertime(Orders orders) {
        if (ObjectUtils.equal(orders.getOrdersStatus(),OrderStatusEnum.NO_PAY.getStatus())
                && orders.getCreateTime().plusMinutes(15).isBefore(LocalDateTime.now())){
            // 查看最新支付情况，如果还没支付再修改
            OrdersPayResDTO ordersPayResDTO = ordersCreateService.getPayResultFromTradServer(orders.getId());
            if (ObjectUtils.notEqual(ordersPayResDTO.getPayStatus(), OrderPayStatusEnum.PAY_SUCCESS.getStatus())){
                // 订单超时未支付
                // 是否使用了优惠劵
                if (ObjectUtils.notEqual(orders.getDiscountAmount(),BigDecimal.ZERO)){
                    // 使用了优惠劵
                    CouponUseBackReqDTO couponUseBackReqDTO = new CouponUseBackReqDTO();
                    couponUseBackReqDTO.setOrdersId(orders.getId());
                    couponUseBackReqDTO.setUserId(orders.getUserId());
                    // 进行优惠劵退回
                    marketClient.useBack(couponUseBackReqDTO);
                }
                // 修改订单状态
                OrderCancelDTO orderCancelDTO = new OrderCancelDTO();
                orderCancelDTO.setId(orders.getId());
                orderCancelDTO.setCancelReason("订单超时未支付");
                orderCancelDTO.setCurrentUserType(UserType.SYSTEM);
                owner.noPayCancelOrders(orderCancelDTO);
                return getById(orders.getId());
            }
        }
        return orders;
    }

    /**
     * 订单评价
     *
     * @param ordersId 订单id
     */
    @Override
    @Transactional
    public void evaluationOrder(Long ordersId) {
//        //查询订单详情
//        Orders orders = queryById(ordersId);
//
//        //构建订单快照
//        OrderSnapshotDTO orderSnapshotDTO = OrderSnapshotDTO.builder()
//                .evaluationTime(LocalDateTime.now())
//                .build();
//
//        //订单状态变更
//        orderStateMachine.changeStatus(orders.getUserId(), orders.getId().toString(), OrderStatusChangeEventEnum.EVALUATE, orderSnapshotDTO);
    }

    /**
     * 取消订单
     * @param orderCancelDTO
     */
    @Override
    public void cancel(OrderCancelDTO orderCancelDTO) {
        // 校验合法性
        Orders orders = this.getById(orderCancelDTO.getId());
        if (ObjectUtils.isEmpty(orders)){
            throw new CommonException("订单不存在");
        }
        // 是否使用了优惠劵
        if (ObjectUtils.notEqual(orders.getDiscountAmount(),BigDecimal.ZERO)){
            // 使用了优惠劵
            CouponUseBackReqDTO couponUseBackReqDTO = new CouponUseBackReqDTO();
            couponUseBackReqDTO.setOrdersId(orders.getId());
            couponUseBackReqDTO.setUserId(orders.getUserId());
            // 进行优惠劵退回
            marketClient.useBack(couponUseBackReqDTO);
        }
        BeanUtils.copyProperties(orders,orderCancelDTO);
        // 订单状态判断，不同订单不同处理
        if (ObjectUtils.equal(orders.getOrdersStatus(), OrderStatusEnum.NO_PAY.getStatus())){
            // 未支付状态下取消
            owner.noPayCancelOrders(orderCancelDTO);
        } else if (ObjectUtils.equal(orders.getOrdersStatus(), OrderStatusEnum.DISPATCHING.getStatus())) {
            // 已支付状态下取消
            owner.cancelByDispatching(orderCancelDTO);
            // 开启一个线程进行退款
            ordersHandler.requestRefundNewThread(orderCancelDTO.getId());
        }else {
            throw new CommonException("该状态不能取消");
        }
    }

    /**
     * 取消未支付订单
     * @param orderCancelDTO
     */
    @GlobalTransactional
    public void noPayCancelOrders(OrderCancelDTO orderCancelDTO) {
        // 设置属性
        OrdersCanceled ordersCanceled = BeanUtils.toBean(orderCancelDTO,OrdersCanceled.class);
        ordersCanceled.setCancellerId(orderCancelDTO.getCurrentUserId());
        ordersCanceled.setCancelerName(orderCancelDTO.getCurrentUserName());
        ordersCanceled.setCancellerType(orderCancelDTO.getCurrentUserType());
        ordersCanceled.setCancelTime(LocalDateTime.now());
        // 更新取消表
        ordersCanceledService.save(ordersCanceled);
        // 修改订单状态
//        OrderSnapshotDTO orderSnapshotDTO = BeanUtils.toBean(ordersCanceled,OrderSnapshotDTO.class);
//        orderStateMachine.changeStatus(this.getById(ordersCanceled.getId()).getUserId(),String.valueOf(orderCancelDTO.getId()), OrderStatusChangeEventEnum.CANCEL,orderSnapshotDTO);
        OrderUpdateStatusDTO orderUpdateStatusDTO = OrderUpdateStatusDTO.builder()
                .id(orderCancelDTO.getId())
                .originStatus(OrderStatusEnum.NO_PAY.getStatus())
                .targetStatus(OrderStatusEnum.CANCELED.getStatus())
                .build();
        Integer insert = ordersCommonService.updateStatus(orderUpdateStatusDTO);
        if (insert <= 0){
            throw new CommonException("未付款状态下取消订单失败");
        }
    }

    /**
     * 派单中的时候取消订单
     * @param orderCancelDTO
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelByDispatching(OrderCancelDTO orderCancelDTO){
        // 设置属性
        OrdersCanceled ordersCanceled = BeanUtils.toBean(orderCancelDTO,OrdersCanceled.class);
        ordersCanceled.setCancellerId(orderCancelDTO.getCurrentUserId());
        ordersCanceled.setCancelerName(orderCancelDTO.getCurrentUserName());
        ordersCanceled.setCancellerType(orderCancelDTO.getCurrentUserType());
        ordersCanceled.setCancelTime(LocalDateTime.now());
        // 更新取消表
        ordersCanceledService.save(ordersCanceled);
        // 修改订单状态
        OrderSnapshotDTO orderSnapshotDTO = BeanUtils.toBean(ordersCanceled,OrderSnapshotDTO.class);
        orderStateMachine.changeStatus(this.getById(ordersCanceled.getId()).getUserId(),String.valueOf(orderCancelDTO.getId()), OrderStatusChangeEventEnum.CANCEL,orderSnapshotDTO);
//        OrderUpdateStatusDTO orderUpdateStatusDTO = OrderUpdateStatusDTO.builder()
//                .id(orderCancelDTO.getId())
//                .originStatus(OrderStatusEnum.DISPATCHING.getStatus())
//                .targetStatus(OrderStatusEnum.CLOSED.getStatus())
//                .refundStatus(OrderRefundStatusEnum.REFUNDING.getStatus())
//                .build();
//        Integer insert = ordersCommonService.updateStatus(orderUpdateStatusDTO);
//        if (insert <= 0){
//            throw new CommonException("派单中状态下取消订单失败");
//        }
        // 插入退款记录表
        OrdersRefund ordersRefund = new OrdersRefund();
        ordersRefund.setId(orderCancelDTO.getId());
        ordersRefund.setTradingOrderNo(orderCancelDTO.getTradingOrderNo());
        ordersRefund.setRealPayAmount(BigDecimal.valueOf(0.01)); // todo 开发金额设置
        ordersRefundService.save(ordersRefund);
    }

    /**
     * 管理端查询订单列表
     * @param orderPageQueryReqDTO
     * @return
     */
    @Override
    public PageResult<OrderSimpleResDTO> adminPageQuery(OrderPageQueryReqDTO orderPageQueryReqDTO) {
        PageResult<Orders> ordersPageResult = PageHelperUtils.selectPage(orderPageQueryReqDTO,
                () -> lambdaQuery()
                        .eq(ObjectUtils.isNotNull(orderPageQueryReqDTO.getOrdersStatus()), Orders::getOrdersStatus, orderPageQueryReqDTO.getOrdersStatus())
                        .eq(ObjectUtils.isNotNull(orderPageQueryReqDTO.getPayStatus()), Orders::getPayStatus, orderPageQueryReqDTO.getPayStatus())
                        .eq(ObjectUtils.isNotNull(orderPageQueryReqDTO.getRefundStatus()), Orders::getRefundStatus, orderPageQueryReqDTO.getRefundStatus())
                        .eq(ObjectUtils.isNotNull(orderPageQueryReqDTO.getUserId()), Orders::getUserId, orderPageQueryReqDTO.getUserId())
                        .eq(ObjectUtils.isNotNull(orderPageQueryReqDTO.getId()), Orders::getId, orderPageQueryReqDTO.getId())
                        .like(ObjectUtils.isNotEmpty(orderPageQueryReqDTO.getContactsPhone()), Orders::getContactsPhone, orderPageQueryReqDTO.getContactsPhone())
                        .in(CollUtils.isNotEmpty(orderPageQueryReqDTO.getOrdersIdList()), Orders::getId, orderPageQueryReqDTO.getOrdersIdList())
                        .gt(ObjectUtils.isNotEmpty(orderPageQueryReqDTO.getMinCreateTime()), Orders::getCreateTime, orderPageQueryReqDTO.getMinCreateTime())
                        .lt(ObjectUtils.isNotEmpty(orderPageQueryReqDTO.getMaxCreateTime()), Orders::getCreateTime, orderPageQueryReqDTO.getMaxCreateTime())
                        .list()
        );
        return PageResult.of(ordersPageResult,OrderSimpleResDTO.class);
    }
}
