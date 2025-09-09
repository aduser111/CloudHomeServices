package com.jzo2o.market.service;

import com.jzo2o.market.constants.RedisConstants;
import com.jzo2o.redis.utils.RedisSyncQueueUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

import static com.jzo2o.market.constants.RedisConstants.RedisKey.COUPON_SEIZE_SYNC_QUEUE_NAME;

/**
 * @author wenhao
 * @version 1.0
 * @description TODO
 */
@SpringBootTest
@Slf4j
public class RedisLuaTest {


    @Resource(name = "redisTemplate")
    RedisTemplate redisTemplate;

    @Resource(name = "lua_test01")
    DefaultRedisScript script;

    @Resource(name = "seizeCouponScript")
    DefaultRedisScript seizeCouponScript;

    //测试lua
    @Test
    public void test_luafirst() {
        //参数1：key ,key1:test_key01  key2:test_key02
        List<String> keys = Arrays.asList("test_key01","test_key02");
        //参数2：传入lua脚本的参数,"field1","aa","field2", "bb"
        Object result = redisTemplate.execute(script, keys, "field1","aa","field2", "bb");
        log.info("执行结果:{}",result);
    }

    @Test
    public void test_luafirst2() {
        //参数1：key ,key1:test_key01
        List<String> keys = Arrays.asList("test_key01{1}","test_key02{1}");
        //参数2：传入lua脚本的参数,"field1","aa","field2", "bb"
        Object result = redisTemplate.execute(script, keys, "field1","aa","field2", "bb");
        log.info("执行结果:{}",result);
    }

    // 测试强优惠劵脚本
    @Test
    public void  test_seizeCouponScript(){
        Long activityId = 1916448924792487936L;
        Long userId = 1906309036621385728L;
        Integer index = (int) (activityId % 10);
        // 抢券同步队列-key
        String couponSeizeSyncRedisKey = RedisSyncQueueUtils.getQueueRedisKey(COUPON_SEIZE_SYNC_QUEUE_NAME,index);
        // 资源库存表-key
        String resourceStockRedisKey = String.format(RedisConstants.RedisKey.COUPON_RESOURCE_STOCK,index);
        // 抢券成功列表-key
        String couponSeizeListRedisKey = String.format(RedisConstants.RedisKey.COUPON_SEIZE_LIST,activityId,index);
        // keys
        List<String> keys = Arrays.asList(couponSeizeSyncRedisKey, resourceStockRedisKey, couponSeizeListRedisKey);
        // 测试
        Object execute = redisTemplate.execute(seizeCouponScript, keys, activityId, userId);
        log.error(execute.toString());
    }

}

