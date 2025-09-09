package com.jzo2o.market.handler;

import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.utils.IdUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.market.constants.RedisConstants;
import com.jzo2o.market.model.domain.Activity;
import com.jzo2o.market.model.domain.Coupon;
import com.jzo2o.market.model.dto.response.ActivityInfoResDTO;
import com.jzo2o.market.service.IActivityService;
import com.jzo2o.market.service.ICouponService;
import com.jzo2o.redis.handler.SyncProcessHandler;
import com.jzo2o.redis.model.SyncMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Component(RedisConstants.RedisKey.COUPON_SEIZE_SYNC_QUEUE_NAME)
@Slf4j
public class SeizeCouponSyncProcessHandler implements SyncProcessHandler<Object> {

    @Resource
    private ICouponService couponService;
    @Resource
    private IActivityService activityService;

    // 批量同步数据
    @Override
    public void batchProcess(List<SyncMessage<Object>> multiData) {

    }

    // 单个同步数据
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void singleProcess(SyncMessage<Object> singleData) {
        // 拿到数据
        Long userId = Long.parseLong(singleData.getKey());
        Object value = singleData.getValue();
        Long activityId = Long.parseLong(value.toString());
        ActivityInfoResDTO activityInfoResDTO = activityService.queryById(activityId);
        if (ObjectUtils.isNull(activityInfoResDTO)){
            throw new CommonException("活动不存在");
        }
        // 写入优惠劵领取表
        Coupon coupon = new Coupon();
        // 数据处理
        coupon.setId(IdUtils.getSnowflakeNextId());
        coupon.setName(activityInfoResDTO.getName());
        coupon.setUserId(userId);
        coupon.setActivityId(activityId);
        coupon.setType(activityInfoResDTO.getType());
        coupon.setDiscountRate(activityInfoResDTO.getDiscountRate());
        coupon.setDiscountAmount(activityInfoResDTO.getDiscountAmount());
        coupon.setAmountCondition(activityInfoResDTO.getAmountCondition());
        coupon.setValidityTime(LocalDateTime.now().plusDays(activityInfoResDTO.getValidityDays()));
        coupon.setStatus(1);
        // 写入优惠劵表
        boolean save = couponService.save(coupon);
        if (!save){
            throw new CommonException("写入优惠劵表失败");
        }
        // 更改活动表库存
        boolean update = activityService.lambdaUpdate()
                .setSql("stock_num = stock_num -1")
                .eq(Activity::getId, activityId)
                .gt(Activity::getStockNum, 0)
                .update();
        if (!update){
            throw new CommonException("扣减活动库存失败");
        }
    }
}
