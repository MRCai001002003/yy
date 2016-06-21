package com.yy.dao;

import java.util.List;

import com.yy.domain.entity.CustomerIncome;

public interface CustomerIncomeDao {
    int deleteByPrimaryKey(Long incomeid);

    int insert(CustomerIncome record);

    int insertSelective(CustomerIncome record);

    CustomerIncome selectByPrimaryKey(Long incomeid);

    int updateByPrimaryKeySelective(CustomerIncome record);

    int updateByPrimaryKey(CustomerIncome record);
    
    List<CustomerIncome> selectByParam(CustomerIncome record);
}
