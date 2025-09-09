package com.jzo2o.orders.manager.controller.consumer;

import cn.hutool.core.bean.BeanUtil;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.orders.dto.request.OrderCancelReqDTO;
import com.jzo2o.api.orders.dto.response.OrderResDTO;
import com.jzo2o.api.orders.dto.response.OrderSimpleResDTO;
import com.jzo2o.common.model.CurrentUserInfo;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.model.dto.request.OrdersPayReqDTO;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author wenhao
 */
@RestController("consumerOrdersController")
@Api(tags = "用户端-订单相关接口")
@RequestMapping("/consumer/orders")
public class ConsumerOrdersController {

    @Resource
    private IOrdersManagerService ordersManagerService;
    @Resource
    private IOrdersCreateService ordersCreateService;

    /**
     * 用户下单
     * @param placeOrderReqDTO
     * @return
     */
    @ApiOperation("下单接口")
    @PostMapping("/place")
    public PlaceOrderResDTO place(@RequestBody PlaceOrderReqDTO placeOrderReqDTO){
        return ordersCreateService.place(placeOrderReqDTO);
    }


    @GetMapping("/{id}")
    @ApiOperation("根据订单id查询")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "订单id", required = true, dataTypeClass = Long.class)
    })
    public OrderResDTO detail(@PathVariable("id") Long id) {
        return ordersManagerService.getDetail(id);
    }
    @GetMapping("/consumerQueryList")
    @ApiOperation("订单滚动分页查询")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "ordersStatus", value = "订单状态，0：待支付，100：派单中，200：待服务，300：服务中，400：待评价，500：订单完成，600：订单取消，700：已关闭", required = false, dataTypeClass = Integer.class),
            @ApiImplicitParam(name = "sortBy", value = "排序字段", required = false, dataTypeClass = Long.class)
    })
    public List<OrderSimpleResDTO> consumerQueryList(@RequestParam(value = "ordersStatus", required = false) Integer ordersStatus,
                                                     @RequestParam(value = "sortBy", required = false) Long sortBy) {
        return ordersManagerService.consumerQueryList(UserContext.currentUserId(), ordersStatus, sortBy);
    }

    /**
     * 订单支付
     * @param id
     * @param ordersPayReqDTO
     * @return
     */
    @PutMapping("/pay/{id}")
    @ApiOperation("订单支付")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "订单id", required = true, dataTypeClass = Long.class)
    })
    public OrdersPayResDTO pay(@PathVariable("id") Long id, @RequestBody OrdersPayReqDTO ordersPayReqDTO){
        return ordersCreateService.pay(id,ordersPayReqDTO);
    }

    /**
     * 查询订单支付结果
     * @param id
     * @return
     */
    @GetMapping("/pay/{id}/result")
    @ApiOperation("查询订单支付结果")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "订单id", required = true, dataTypeClass = Long.class)
    })
    public OrdersPayResDTO payResult(@PathVariable("id") Long id) {
        return ordersCreateService.getPayResultFromTradServer(id);
    }

    /**
     * 用户取消订单
     * @param orderCancelReqDTO
     */
    @PutMapping("/cancel")
    @ApiOperation("用户取消订单")
    public void cancel(@RequestBody OrderCancelReqDTO orderCancelReqDTO){
        OrderCancelDTO orderCancelDTO = BeanUtils.copyBean(orderCancelReqDTO, OrderCancelDTO.class);
        // 数据处理 添加当前用户数据
        orderCancelDTO.setCurrentUserId(UserContext.currentUserId());
        orderCancelDTO.setCurrentUserName(UserContext.currentUser().getName());
        orderCancelDTO.setCurrentUserType(UserContext.currentUser().getUserType());
        // 取消订单
        ordersManagerService.cancel(orderCancelDTO);
    }

    /**
     * 订单服务查询可用优惠劵
     * @param serveId
     * @param purNum
     * @return
     */
    @GetMapping("/getAvailableCoupons")
    @ApiOperation("订单服务查询可用优惠劵")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "serveId", value = "服务id", required = true, dataTypeClass = Long.class),
            @ApiImplicitParam(name = "purNum", value = "购买数量，默认1", required = false, dataTypeClass = Integer.class)
    })
    public List<AvailableCouponsResDTO> getAvailableCoupons(Long serveId, Integer purNum){
        return ordersCreateService.getAvailableCoupons(serveId,purNum);
    }
}
