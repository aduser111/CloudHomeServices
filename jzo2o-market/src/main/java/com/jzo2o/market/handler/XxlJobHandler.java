package com.jzo2o.market.handler;

import com.jzo2o.market.constants.RedisConstants;
import com.jzo2o.market.service.IActivityService;
import com.jzo2o.market.service.ICouponService;
import com.jzo2o.redis.annotations.Lock;
import com.jzo2o.redis.constants.RedisSyncQueueConstants;
import com.jzo2o.redis.sync.SyncManager;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.concurrent.ThreadPoolExecutor;

import static com.jzo2o.market.constants.RedisConstants.Formatter.*;
import static com.jzo2o.market.constants.RedisConstants.RedisKey.COUPON_SEIZE_SYNC_QUEUE_NAME;

@Component
@Slf4j
public class XxlJobHandler {

    @Resource
    private SyncManager syncManager;
    @Resource
    private IActivityService activityService;
    @Resource
    private ICouponService couponService;
    @Resource(name = "syncThreadPool")
    private ThreadPoolExecutor syncThreadPool;


    /**
     * 用户领取优惠劵数据同步从Redis到Mysql
     */
    @XxlJob("seizeCouponSyncJob")
    public void seizeCouponSyncJob(){
        /**
         * 开始同步，可以使用自定义线程池，如果不设置使用默认线程池
         * @param queueName 同步队列名称
         * @param storageType 数据存储类型，1：redis hash数据结构，2：redis list数据结构，3：redis zSet结构
         * @param mode 1 单条执行 2批量执行
         * @param dataSyncExecutor 数据同步线程池
         */
        syncManager.start(COUPON_SEIZE_SYNC_QUEUE_NAME,1,1,syncThreadPool);
    }

    /**
     * 活动状态修改，
     * 1.活动进行中状态修改
     * 2.活动已失效状态修改
     * 每分钟执行一次
     */
    @XxlJob("updateActivityStatus")
    public void updateActivityStatus(){
        log.info("定时修改活动状态...");
        try {
            activityService.updateStatus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 活动预热，整点预热
     *
     */
    @XxlJob("activityPreheat")
    public void activityPreHeat() {
        log.info("优惠券活动定时预热...");
        try {
            activityService.preHeat();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 已领取优惠券自动过期任务
     * 每小时执行一次
     */
    @XxlJob("processExpireCoupon")
    public void processExpireCoupon() {
        log.info("已领取优惠券自动过期任务...");
        try {
            couponService.processExpireCoupon();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
