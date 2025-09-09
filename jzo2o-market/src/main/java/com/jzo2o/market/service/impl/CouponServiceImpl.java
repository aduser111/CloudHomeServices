package com.jzo2o.market.service.impl;

import cn.hutool.db.DbRuntimeException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.api.market.dto.request.CouponUseReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.market.dto.response.CouponUseResDTO;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.*;
import com.jzo2o.market.constants.RedisConstants;
import com.jzo2o.market.enums.ActivityStatusEnum;
import com.jzo2o.market.enums.CouponStatusEnum;
import com.jzo2o.market.mapper.CouponMapper;
import com.jzo2o.market.model.domain.Activity;
import com.jzo2o.market.model.domain.Coupon;
import com.jzo2o.market.model.domain.CouponUseBack;
import com.jzo2o.market.model.domain.CouponWriteOff;
import com.jzo2o.market.model.dto.request.CouponOperationPageQueryReqDTO;
import com.jzo2o.market.model.dto.request.SeizeCouponReqDTO;
import com.jzo2o.market.model.dto.response.ActivityInfoResDTO;
import com.jzo2o.market.model.dto.response.CouponInfoResDTO;
import com.jzo2o.market.service.IActivityService;
import com.jzo2o.market.service.ICouponService;
import com.jzo2o.market.service.ICouponUseBackService;
import com.jzo2o.market.service.ICouponWriteOffService;
import com.jzo2o.market.utils.CouponUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.mysql.utils.PageUtils;
import com.jzo2o.redis.utils.RedisSyncQueueUtils;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.jzo2o.common.constants.ErrorInfo.Code.SEIZE_COUPON_FAILD;
import static com.jzo2o.market.constants.RedisConstants.RedisKey.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wenhao
 */
@Service
@Slf4j
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    @Resource(name = "seizeCouponScript")
    private DefaultRedisScript<String> seizeCouponScript;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private IActivityService activityService;

    @Resource
    private ICouponUseBackService couponUseBackService;

    @Resource
    private ICouponWriteOffService couponWriteOffService;

    @Override
    public PageResult<CouponInfoResDTO> queryForPageOfOperation(CouponOperationPageQueryReqDTO couponOperationPageQueryReqDTO) {
        // 1.数据校验
        if (ObjectUtils.isNull(couponOperationPageQueryReqDTO.getActivityId())) {
            throw new BadRequestException("请指定活动");
        }
        // 2.数据查询
        // 分页 排序
        Page<Coupon> couponQueryPage = PageUtils.parsePageQuery(couponOperationPageQueryReqDTO, Coupon.class);
        // 查询条件
        LambdaQueryWrapper<Coupon> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Coupon::getActivityId, couponOperationPageQueryReqDTO.getActivityId());
        // 查询数据
        Page<Coupon> couponPage = baseMapper.selectPage(couponQueryPage, lambdaQueryWrapper);

        // 3.数据转化，并返回
        return PageUtils.toPage(couponPage, CouponInfoResDTO.class);
    }

    @Override
    public List<CouponInfoResDTO> queryForList(Long lastId, Long userId, Integer status) {

        // 1.校验
        if (status > 3 || status < 1) {
            throw new BadRequestException("请求状态不存在");
        }
        // 2.查询准备
        LambdaQueryWrapper<Coupon> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 查询条件
        lambdaQueryWrapper.eq(Coupon::getStatus, status)
                .eq(Coupon::getUserId, userId)
                .lt(ObjectUtils.isNotNull(lastId), Coupon::getId, lastId);
        // 查询字段
        lambdaQueryWrapper.select(Coupon::getId);
        // 排序
        lambdaQueryWrapper.orderByDesc(Coupon::getId);
        // 查询条数限制
        lambdaQueryWrapper.last(" limit 10 ");
        // 3.查询数据(数据中只含id)
        List<Coupon> couponsOnlyId = baseMapper.selectList(lambdaQueryWrapper);
        //判空
        if (CollUtils.isEmpty(couponsOnlyId)) {
            return new ArrayList<>();
        }

        // 4.获取数据且数据转换
        // 优惠id列表
        List<Long> ids = couponsOnlyId.stream()
                .map(Coupon::getId)
                .collect(Collectors.toList());
        // 获取优惠券数据
        List<Coupon> coupons = baseMapper.selectBatchIds(ids);
        // 数据转换
        return BeanUtils.copyToList(coupons, CouponInfoResDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long activityId) {
        lambdaUpdate()
                .set(Coupon::getStatus, CouponStatusEnum.VOIDED.getStatus())
                .eq(Coupon::getActivityId, activityId)
                .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())
                .update();
    }

    @Override
    public Integer countReceiveNumByActivityId(Long activityId) {
        return lambdaQuery().eq(Coupon::getActivityId, activityId)
                .count();
    }

    @Override
    public void processExpireCoupon() {
        lambdaUpdate()
                .set(Coupon::getStatus, CouponStatusEnum.INVALID.getStatus())
                .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())
                .le(Coupon::getValidityTime, DateUtils.now())
                .update();
    }

    /**
     * 用户领取优惠劵
     * @param seizeCouponReqDTO
     */
    @Override
    public void seizeCoupon(SeizeCouponReqDTO seizeCouponReqDTO) {
        // 查找活动并校验
        ActivityInfoResDTO activityInfoResDTO = activityService.getActivityInfoByCache(seizeCouponReqDTO.getId());
        if (ObjectUtils.isNull(activityInfoResDTO) || activityInfoResDTO.getDistributeEndTime().isBefore(LocalDateTime.now())){
            throw new CommonException(SEIZE_COUPON_FAILD, "活动已结束");
        }
        if (activityInfoResDTO.getDistributeStartTime().isAfter(LocalDateTime.now())){
            throw new CommonException(SEIZE_COUPON_FAILD, "活动未开始");
        }

        // 获取数据，执行lua脚本
        Long activityId = seizeCouponReqDTO.getId();
        Long userId = UserContext.currentUserId();
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
        log.info("lua脚本执行结果：{}",execute);
        // 结果校验是否异常
        Long executeInteger = Long.parseLong(execute.toString());
        if (executeInteger == -1){
            throw new CommonException(SEIZE_COUPON_FAILD, "只能领取一次");
        }
        if (executeInteger == -2 || executeInteger == -4){
            throw new CommonException(SEIZE_COUPON_FAILD, "已抢光");
        }
        if (executeInteger == -3 || executeInteger == -5){
            throw new CommonException(SEIZE_COUPON_FAILD, "抢券失败");
        }
    }

    /**
     * 获取可用优惠券列表
     * @param totalAmount
     * @return
     */
    @Override
    public List<AvailableCouponsResDTO> getAvailable(BigDecimal totalAmount) {
        // 获取用户id
        Long userId = UserContext.currentUserId();
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getUserId, userId)
                .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())
                .gt(Coupon::getValidityTime, DateUtils.now())
                .le(Coupon::getAmountCondition, totalAmount)
                .list();
        // 判空
        if (CollUtils.isEmpty(coupons)) {
            return new ArrayList<>();
        }

        // 2.组装数据计算优惠金额
        List<AvailableCouponsResDTO> collect = coupons.stream()
                .peek(coupon -> coupon.setDiscountAmount(CouponUtils.calDiscountAmount(coupon, totalAmount)))
                //过滤优惠金额大于0且小于订单金额的优惠券
                .filter(coupon -> coupon.getDiscountAmount().compareTo(new BigDecimal(0)) > 0 && coupon.getDiscountAmount().compareTo(totalAmount) < 0)
                // 类型转换
                .map(coupon -> BeanUtils.copyBean(coupon, AvailableCouponsResDTO.class))
                //按优惠金额降序排
                .sorted(Comparator.comparing(AvailableCouponsResDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
        return collect;
    }

    /**
     * 核销优惠劵
     * @param couponUseReqDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponUseResDTO use(CouponUseReqDTO couponUseReqDTO) {
        Coupon coupon = getById(couponUseReqDTO.getId());
        if (ObjectUtils.isNull(coupon) || coupon.getStatus() != CouponStatusEnum.NO_USE.getStatus()){
            throw new CommonException("优惠劵错误");
        }
        if (coupon.getValidityTime().isBefore(LocalDateTime.now())){
            throw new CommonException("优惠劵已过期");
        }
        if (ObjectUtils.notEqual(UserContext.currentUserId(),coupon.getUserId())){
            throw new CommonException("只能使用自己的优惠劵");
        }
        // 更新优惠劵表状态
        boolean update = lambdaUpdate()
                .eq(Coupon::getId, couponUseReqDTO.getId())
                .le(Coupon::getAmountCondition, couponUseReqDTO.getTotalAmount())
                .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())
                .set(Coupon::getUseTime, LocalDateTime.now())
                .set(Coupon::getStatus, CouponStatusEnum.USED.getStatus())
                .set(Coupon::getOrdersId, couponUseReqDTO.getOrdersId())
                .update();
        if (!update){
            throw new CommonException("优惠劵状态更新失败");
        }
        // 保存优惠劵核销表
        CouponWriteOff couponWriteOff = new CouponWriteOff();
        couponWriteOff.setId(IdUtils.getSnowflakeNextId());
        couponWriteOff.setCouponId(couponUseReqDTO.getId());
        couponWriteOff.setUserId(UserContext.currentUserId());
        couponWriteOff.setOrdersId(couponUseReqDTO.getOrdersId());
        couponWriteOff.setActivityId(coupon.getActivityId());
        couponWriteOff.setWriteOffTime(LocalDateTime.now());
        boolean save = couponWriteOffService.save(couponWriteOff);
        if (!save){
            throw new CommonException("保存优惠劵核销表失败");
        }
        // 返回优惠金额
        BigDecimal discountAmount = CouponUtils.calDiscountAmount(coupon, couponUseReqDTO.getTotalAmount());
        CouponUseResDTO couponUseResDTO = new CouponUseResDTO();
        couponUseResDTO.setDiscountAmount(discountAmount);
        return couponUseResDTO;
    }

    /**
     * 优惠劵退回
     * @param couponUseBackReqDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void useBack(CouponUseBackReqDTO couponUseBackReqDTO) {
        // 查询优惠劵核销表
        CouponWriteOff couponWriteOff = couponWriteOffService.lambdaQuery()
                .eq(CouponWriteOff::getOrdersId, couponUseBackReqDTO.getOrdersId())
                .one();
        if (ObjectUtils.isNull(couponWriteOff)){
            throw new CommonException("优惠劵核销表数据错误");
        }
        couponUseBackReqDTO.setId(couponWriteOff.getCouponId());
        // 优惠劵退回
        Coupon coupon = getById(couponUseBackReqDTO.getId());
        if (ObjectUtils.isNull(coupon) || coupon.getStatus() != CouponStatusEnum.USED.getStatus()){
            throw new CommonException("优惠劵错误");
        }
        // 优惠劵状态确定
        Integer status = coupon.getValidityTime().isAfter(LocalDateTime.now()) ?
                CouponStatusEnum.NO_USE.getStatus() : CouponStatusEnum.VOIDED.getStatus();
        // 核销时间
        LocalDateTime useTime = coupon.getUseTime();
        // 更新优惠劵表
        boolean update = lambdaUpdate()
                .eq(Coupon::getId, couponUseBackReqDTO.getId())
                .eq(Coupon::getStatus, CouponStatusEnum.USED.getStatus())
                .setSql("use_time = NULL")           // 直接SQL设置NULL
                .set(Coupon::getStatus, status)
                .setSql("orders_id = NULL")          // 直接SQL设置NULL
                .update();
        if (!update){
            throw new CommonException("优惠劵表更新失败");
        }
        // 保存优惠劵退回表
        CouponUseBack couponUseBack = new CouponUseBack();
        couponUseBack.setId(IdUtils.getSnowflakeNextId());
        couponUseBack.setCouponId(coupon.getId());
        couponUseBack.setUserId(coupon.getUserId());
        couponUseBack.setUseBackTime(LocalDateTime.now());
        couponUseBack.setWriteOffTime(useTime);
        boolean save = couponUseBackService.save(couponUseBack);
        if (!save){
            throw new CommonException("保存优惠劵退回表失败");
        }
        // 删除优惠劵核销表
        boolean remove = couponWriteOffService.removeById(couponWriteOff.getId());
        if (!remove){
            throw new CommonException("删除优惠劵核销表失败");
        }
    }
}
