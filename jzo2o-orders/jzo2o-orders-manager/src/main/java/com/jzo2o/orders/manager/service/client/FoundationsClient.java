package com.jzo2o.orders.manager.service.client;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.ServeApi;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author wh
 * @version 1.0
 * @description 调用foundations的客户端类
 * @date 2025/04/15 16:46
 */
@Component
@Slf4j
public class FoundationsClient {

    @Resource
    private ServeApi serveApi;

    @SentinelResource(value = "getServeDetail", fallback = "serveDetailFallback", blockHandler = "serveDetailBlockHandler")
    public ServeAggregationResDTO getServeDetail(Long id){
        log.error("根据id查询服务项，id:{}", id);
        ServeAggregationResDTO serveResDTO = serveApi.findById(id);
        return serveResDTO;
    }

    //执行异常走
    public AddressBookResDTO serveDetailFallback(Long id, Throwable throwable) {
        log.error("非限流、熔断等导致的异常执行的降级方法，id:{},throwable:", id, throwable);
        return null;
    }

    //熔断后的降级逻辑
    public AddressBookResDTO serveDetailBlockHandler(Long id, BlockException blockException) {
        log.error("触发限流、熔断时执行的降级方法，id:{},blockException:", id, blockException);
        return null;
    }
}
