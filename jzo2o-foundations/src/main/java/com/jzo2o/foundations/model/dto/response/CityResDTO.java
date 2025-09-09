package com.jzo2o.foundations.model.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 城市响应信息
 *
 * @author wenhao
 **/
@Data
@ApiModel("城市响应信息")
public class CityResDTO {
    /**
     * 城市名称
     */
    @ApiModelProperty("城市名称")
    private String cityName;

    /**
     * 城市编码
     */
    @ApiModelProperty("城市编码")
    private String cityCode;
}
