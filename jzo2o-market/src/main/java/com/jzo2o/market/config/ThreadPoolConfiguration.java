package com.jzo2o.market.config;

import com.jzo2o.redis.properties.RedisSyncProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

/**
 * 线程池配置类
 */
@Configuration
public class ThreadPoolConfiguration {

    @Bean("syncThreadPool")
    public ThreadPoolExecutor synchronizeThreadPool(RedisSyncProperties redisSyncProperties){
        // 准备参数
        // 同步队列数量
        int queueNum = redisSyncProperties.getQueueNum();
        //核心线程数 corePoolSize
        int corePoolSize = 1;
        // 最大线程数量 maximumPoolSize
        int maximumPoolSize = queueNum;
        // 空闲时间 keepAliveTime
        long keepAliveTime = 120;
        // 时间单位 unit
        TimeUnit unit = TimeUnit.SECONDS;
        // 任务队列 SynchronousQueue 不存储任务
        BlockingQueue<Runnable> workQueue = new SynchronousQueue<>();
        // 拒绝策略 默认
        RejectedExecutionHandler handler = new ThreadPoolExecutor.DiscardPolicy();
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                handler
        );
        return threadPoolExecutor;
    }
}
