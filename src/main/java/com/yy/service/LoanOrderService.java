package com.yy.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.yy.common.exception.CustomException;
import com.yy.dao.LoanOrderDao;
import com.yy.domain.entity.Customer;
import com.yy.domain.entity.LoanOrder;
import com.yy.web.utils.StringUtil;

@Service
public class LoanOrderService {
	@Autowired
	LoanOrderDao loanOrderDao;
	@Autowired
	private CustomerService customerService;
	/**
	 * @Title: saveOrUpLoadOrder 
	 * @Description: 新增、修改贷款记录
	 * @author caiZhen
	 * @date 2016年6月11日 下午7:06:45
	 * @param @param request
	 * @param @param loanOrder    设定文件 
	 * @return void    返回类型 
	 * @throws
	 */
	public void saveOrUpLoadOrder(HttpServletRequest request,LoanOrder loanOrder){
		List<LoanOrder> loanOrderList = loanOrderDao.selectByParam(new LoanOrder(null,loanOrder.getCustomerID(),"P"));
		if(loanOrderList!=null&&loanOrderList.size()>0){
			throw new CustomException("该用户有在审批业务");
		}
		if(StringUtils.isBlank(loanOrder.getLoanOrderCode())){
			loanOrder.setOrderDate(new Date());
			//新增初始化部分信息
			loanOrder.setLoanAmount(loanOrder.getLoanAmount()*10000);//将万元转化成元
			loanOrder.setProductCode("16001001");
			loanOrder.setLoanTitle("保险-现金循环贷");
			loanOrder.setSalerID(3l);
			loanOrder.setOrderStatus("P");
			loanOrder.setInterestRate(new BigDecimal("0.1500"));
			loanOrder.setLoanOrderCode(getLoanOrderCode());
			
			loanOrderDao.insert(loanOrder);
		}else{
			loanOrderDao.updateByPrimaryKeySelective(loanOrder);
		}
	}
	/**
	 * @Title: saveCustomerLoan 
	 * @Description: 保存信息 
	 * @author caiZhen
	 * @date 2016年6月6日 上午11:09:46
	 * @param @param request
	 * @param @param loanOrder    设定文件 
	 * @return void    返回类型 
	 */
	public String saveCustomerLoan(HttpServletRequest request,LoanOrder loanOrder){
		String str="";
		String verificationCode = (String)StringUtil.getSession(request, "verificationCode");
		String phone = (String)StringUtil.getSession(request, "phone");
		if(StringUtils.isBlank(verificationCode)||StringUtils.isBlank(phone)){
			throw new CustomException("验证码已过期,请重置");
		}
		if(!phone.equals(loanOrder.getCellPhone())){
			throw new CustomException("您输入手机号与收验证码手机号不一致");
		}
		String code=request.getParameter("code");
		if(!verificationCode.equals(code)){
			throw new CustomException("请核对验证码");
		}
		//增加贷款记录前，查看有无客户信息(有：获取客户id，无：增加)
		Customer customer = new Customer(loanOrder.getCellPhone());
		List<Customer> listCustomer = customerService.getCustomer(customer);
		//设置customerid
		if(listCustomer!=null&&listCustomer.size()>0){
			customer=listCustomer.get(0);
			StringUtil.setSession(request, customer, "customer");
			//贷款申请未完成 则进入相应的完善信息界面
			if(StringUtils.isNotBlank(customer.getCustomerStatus())){
				//DRAFT、BASIC状态更新借款金额、借款期限
				if("DRAFT".equals(customer.getCustomerStatus())||"BASIC".equals(customer.getCustomerStatus())){
					loanOrder.setLoanAmount(loanOrder.getLoanAmount()*10000);//将万元转化成元
					loanOrder.setCustomerID(customer.getCustomerID());
					loanOrderDao.updateByCustomerID(loanOrder);
					return customer.getCustomerStatus();
				}
			}
			str = "exist";//该用户有申请记录且信息完善，进入registerInfo界面
			loanOrder.setCustomerID(customer.getCustomerID());
		}else{
			customerService.saveOrUpCustomer(request,customer);
			loanOrder.setCustomerID(customer.getCustomerID());
		}
		saveOrUpLoadOrder(request,loanOrder);
		
		return str;
	}
	/**
	 * 
	 * @return BA+年月日+6位流水号
	 */
	private String getLoanOrderCode(){
		return new StringBuilder("BA"+StringUtil.getFormatDate(new Date(), "yyyyMMdd")+StringUtil.randomCode(6)).toString();
	}
	/**
	 * @Title: getLoanOrders 
	 * @Description: 保存信息 
	 * @author caiZhen
	 * @date 2016年7月13日  下午6:37:46
	 * @param @param request
	 * @return List<LoanOrder> 
	 */
	public List<LoanOrder> getLoanOrders(HttpServletRequest request){
		Customer c=(Customer)request.getSession().getAttribute("customer");
		if(c==null){
			throw new CustomException("会话消失");
		}
		LoanOrder loanOrder = new LoanOrder();
		return loanOrderDao.selectByParam(loanOrder);
	}
	public LoanOrder getLoadOrderDetail(HttpServletRequest request){
		return loanOrderDao.selectByPrimaryKey(request.getParameter("loanOrderId"));
	}
	public Map<String,String> selectObject(HttpServletRequest request){
		Customer c=(Customer)request.getSession().getAttribute("customer");
		if(c==null){
			throw new CustomException("会话消失");
		}
		if(c==null){
			throw new CustomException("会话消失");
		}
		Map<String,String> map=loanOrderDao.selectObject(new LoanOrder(c.getCustomerID()));
		return map;
	}
}
