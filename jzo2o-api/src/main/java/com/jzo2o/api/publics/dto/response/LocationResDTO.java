package com.jzo2o.api.publics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 经纬度
 *
 * @author wenhao
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationResDTO {
    /**
     * 经纬度
     */
    private String location;
}
