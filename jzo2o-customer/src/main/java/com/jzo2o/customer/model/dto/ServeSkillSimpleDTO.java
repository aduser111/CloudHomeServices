package com.jzo2o.customer.model.dto;

import lombok.Data;

/**
 * 服务技能简略信息
 *
 * @author wenhao
 **/
@Data
public class ServeSkillSimpleDTO {
    /**
     * 服务类型名称
     */
    private String serveTypeName;

    /**
     * 服务项名称
     */
    private String serveItemName;
}
