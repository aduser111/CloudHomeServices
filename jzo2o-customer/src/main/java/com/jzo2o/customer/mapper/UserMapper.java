package com.jzo2o.customer.mapper;

import co.elastic.clients.elasticsearch.security.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jzo2o.customer.model.domain.AddressBook;
import org.apache.ibatis.annotations.Select;
import org.springframework.boot.autoconfigure.security.SecurityProperties;

public interface UserMapper extends BaseMapper<User> {

    @Select("")
    String getPasswordByNumber();
}
