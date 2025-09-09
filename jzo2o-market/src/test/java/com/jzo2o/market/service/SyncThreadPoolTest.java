package com.jzo2o.market.service;

import com.jzo2o.redis.model.SyncMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
@Slf4j
public class SyncThreadPoolTest {

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private RedissonClient redissonClient;

    // 任务类 执行任务无返回值
    public class RunnableSimple implements Runnable {
        // 任务序号
        private Integer index;

        public RunnableSimple(Integer index) {
            this.index = index;
        }

        @Override
        public void run() {
            // 执行任务
            log.info("{}线程池执行任务:{}", Thread.currentThread().getId(), index);
            String key = String.format("QUEUE:COUPON:SEIZE:SYNC:{%s}", index);
            // 读取数据，进行redis到mysql同步
            getDate(key);
            /*try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }*/
        }
    }

    public class RunnableSimple2 implements Runnable {
        // 任务序号
        private Integer index;

        public RunnableSimple2(Integer index) {
            this.index = index;
        }

        @Override
        public void run() {
            String lockKey = String.format("LOCK:QUEUE:COUPON:SEIZE:SYNC:{%s}", index);
            String key = String.format("QUEUE:COUPON:SEIZE:SYNC:{%s}", index);
            RLock lock = redissonClient.getLock(lockKey);
            try {
                boolean tryLock = lock.tryLock(3, -1, TimeUnit.SECONDS);
                if (tryLock) {
                    // 获取锁成功
                    //执行任务
                    log.info("{}执行任务:{}", Thread.currentThread().getId(), index);

                    log.info("{}开始获取{}队列的数据", Thread.currentThread().getId(), key);

                    getDate(key);
                    //模拟执行任务的时长
                    Thread.sleep(50000);
                } else {
                    // 获取锁失败
                    log.info("!!!!!!{}获取{}队列的锁失败", Thread.currentThread().getId(), key);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
            // 执行任务
//            log.info("{}线程池执行任务:{}",Thread.currentThread().getId(),index);
//            String key = String.format("QUEUE:COUPON:SEIZE:SYNC:{%s}",index);
//            // 读取数据，进行redis到mysql同步
//            getDate(key);
            /*try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }*/
        }
    }

    /**
     * 从同步队列拿数据
     *
     * @param key
     */
    public void getDate(String key) {
        Cursor<Map.Entry<String, Object>> scan = null;
        try {
            ScanOptions scanOptions = ScanOptions.scanOptions().count(10).build();
            scan = redisTemplate.opsForHash().scan(key, scanOptions);
            List<SyncMessage<Object>> collect = scan.stream().map(item -> SyncMessage.builder()
                    .key(item.getKey())
                    .value(item.getValue())
                    .build()).collect(Collectors.toList());
            log.info("{}线程从{}队列获取了{}条数据", Thread.currentThread().getId(), key, collect.size());
            collect.forEach(System.out::println);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (scan != null) {
                scan.close();
            }
        }
    }

    @Resource(name = "syncThreadPool")
    private ThreadPoolExecutor threadPoolExecutor;

    @Test
    public void test_threadPool() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            threadPoolExecutor.execute(new RunnableSimple(i));
        }
        Thread.sleep(999999);
//        for (int i = 10; i < 20; i++) {
//            threadPoolExecutor.execute(new RunnableSimple(i));
//        }
    }

    @Test
    public void test_lock() throws InterruptedException {
        threadPoolExecutor.execute(new RunnableSimple2(6));
        Thread.sleep(5000);
        threadPoolExecutor.execute(new RunnableSimple2(6));
        Thread.sleep(999999);
//        for (int i = 10; i < 20; i++) {
//            threadPoolExecutor.execute(new RunnableSimple(i));
//        }
    }
}
