package com.jzo2o.customer.controller.open;

import com.jzo2o.common.constants.UserType;
import com.jzo2o.common.model.Result;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.common.utils.StringUtils;
import com.jzo2o.customer.model.dto.request.LoginForCustomerReqDTO;
import com.jzo2o.customer.model.dto.request.LoginForWorkReqDTO;
import com.jzo2o.customer.model.dto.response.LoginResDTO;
import com.jzo2o.customer.service.ILoginService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author wenhao
 */
@RestController("openLoginController")
@RequestMapping("/open/login")
@Api(tags = "白名单接口 - 客户登录相关接口")
public class LoginController {

    @Resource
    private ILoginService loginService;

    @PostMapping("/worker")
    @ApiOperation("服务人员/机构人员登录接口")
    public LoginResDTO loginForWorker(@RequestBody LoginForWorkReqDTO loginForWorkReqDTO) {

        if(UserType.INSTITUTION == loginForWorkReqDTO.getUserType()){
            return loginService.loginForPassword(loginForWorkReqDTO);
        }else{
            return loginService.loginForVerify(loginForWorkReqDTO);
        }

    }

    /**
     * c端用户登录接口
     */
    @PostMapping("/common/user")
    @ApiOperation("c端用户登录接口")
    public LoginResDTO loginForCommonUser(@RequestBody LoginForCustomerReqDTO loginForCustomerReqDTO) {
        return loginService.loginForCommonUser(loginForCustomerReqDTO);
    }

    /**
     * 用户登录
     * @param number
     * @param password
     * @return
     */
    @PostMapping("/login")
    @ApiOperation("用户登录接口")
    public Result<LoginResDTO> login(@RequestParam("number") Long number,@RequestParam("password") String password) {
        // 参数校验
        if (ObjectUtils.isNull(number) || StringUtils.isEmpty(password)){
            return Result.error("登录参数错误");
        }
        // 登录
        return  loginService.login(number, password);
    }
}
