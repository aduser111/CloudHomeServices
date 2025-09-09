package com.jzo2o.orders.manager.service.client;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.jzo2o.api.customer.AddressBookApi;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.market.CouponApi;
import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.api.market.dto.request.CouponUseReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.market.dto.response.CouponUseResDTO;
import com.jzo2o.common.utils.CollUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * @author wh
 * @version 1.0
 * @description 调用customer的客户端类
 * @date 2025/04/13 17:46
 */
@Component
@Slf4j
public class MarketClient {

    @Resource
    private CouponApi couponApi;

    /**
     * 查询可用优惠劵
     * @param totalAmount
     * @return
     */
    @SentinelResource(value = "getAvailableCoupon", fallback = "availableFallback", blockHandler = "availableBlockHandler")
    public List<AvailableCouponsResDTO> getAvailable(BigDecimal totalAmount) {
        // 调用其他微服务方法
        List<AvailableCouponsResDTO> list  = couponApi.getAvailable(totalAmount);
        return list;
    }

    //执行异常走
    public List<AvailableCouponsResDTO> availableFallback(BigDecimal totalAmount, Throwable throwable) {
        return Collections.emptyList();
    }

    //熔断后的降级逻辑
    public List<AvailableCouponsResDTO> availableBlockHandler(BigDecimal totalAmount, BlockException blockException) {
        return CollUtils.emptyList();
    }

    /**
     * 优惠劵核销
     * @param couponUseReqDTO
     * @return
     */
    @SentinelResource(value = "useCoupon", fallback = "useFallback", blockHandler = "useBlockHandler")
    public CouponUseResDTO use(CouponUseReqDTO couponUseReqDTO){
        return couponApi.use(couponUseReqDTO);
    }

    //执行异常走
    public CouponUseResDTO useFallback(CouponUseReqDTO couponUseReqDTO, Throwable throwable) {
        return null;
    }

    //熔断后的降级逻辑
    public CouponUseResDTO useFallback(CouponUseReqDTO couponUseReqDTO, BlockException blockException) {
        return null;
    }

    /**
     * 优惠劵退回
     * @param couponUseBackReqDTO
     */
    @SentinelResource(value = "useBackCoupon", fallback = "useBackFallback", blockHandler = "useBackBlockHandler")
    public void useBack(CouponUseBackReqDTO couponUseBackReqDTO){
        couponApi.useBack(couponUseBackReqDTO);
    }

    //执行异常走
    public void useFallback(CouponUseBackReqDTO couponUseBackReqDTO, Throwable throwable) {
    }

    //熔断后的降级逻辑
    public void useFallback(CouponUseBackReqDTO couponUseBackReqDTO, BlockException blockException) {
    }


}