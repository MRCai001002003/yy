package com.yy.service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.yy.common.exception.CustomException;
import com.yy.dao.AccountDao;
import com.yy.dao.CardDao;
import com.yy.dao.CustomerContactorDao;
import com.yy.dao.CustomerDao;
import com.yy.dao.CustomerIncomeDao;
import com.yy.dao.WhiteListDao;
import com.yy.domain.entity.Account;
import com.yy.domain.entity.Card;
import com.yy.domain.entity.Customer;
import com.yy.domain.entity.CustomerCertificate;
import com.yy.domain.entity.CustomerContactor;
import com.yy.domain.entity.CustomerIncome;
import com.yy.domain.entity.WhiteList;
import com.yy.web.utils.HttpXmlClient;
import com.yy.web.utils.StringUtil;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
/**
 * @ClassName: CustomerService
 * @Description: 客户管理服务类
 * @author caizhen
 * @date 2016年5月23日 下午8:35:03
 */
@Service
public class CustomerService {
	private final Logger log = LoggerFactory.getLogger(this.getClass().getName());
	
	@Autowired
	CustomerDao customerDao;
	@Autowired
	CardDao cardDao;
	@Autowired
	AccountDao accountDao;
	@Autowired
	CustomerIncomeDao customerIncomeDao;
	@Autowired
	WhiteListDao whiteListDao;
	@Autowired
	CustomerContactorDao customerContactorDao;
	
	@Autowired
	CustomerCertificateService customerCertificateService;
	@Autowired
	CustomerWorkexperienceService customerWorkexperienceService;
	@Autowired
	CustomerEducationService customerEducationService;
	@Autowired
	CustomerPersonalService customerPersonalService;
	//是否获取聚信力信息
	@Value("#{settings['is_get_juxinli_data']}")
	private boolean is_get_juxinli_data;
	//服务器IP
	@Value("#{settings['server_ip']}")
	private String server_ip;
	/**
	 *
	 * @Title: saveOrUpCustomer
	 * @Description: 更新或修改客户信息
	 * @author caizhen
	 * @param @param customer    设定文件
	 * @return void    返回类型
	 */
	public void saveOrUpCustomer(HttpServletRequest request,Customer customer){
		if(customer!=null&&customer.getCustomerID()!=null){
			customer.setLastLoginTime(new Date());
			customerDao.updateByPrimaryKeySelective(customer);
		}else{
			customer.setCreateTime(new Date());
			customer.setCustomerStatus("DRAFT");//仅存在电话、贷款信息
			customerDao.insertSelective(customer);
			this.saveCustomerIncome(request, customer);//白名单中同步客户的收入、地址信息
		}
		customer=customerDao.selectByPrimaryKey(customer.getCustomerID());
		StringUtil.setSession(request, customer, "customer");
	}
	/**
	 *
	 * @Title: saveCustomerIncome
	 * @Description: 白名单中同步客户的收入、地址信息
	 * @author caizhen
	 * @param @param customer    设定文件
	 * @return void    返回类型
	 */
	public void saveCustomerIncome(HttpServletRequest request,Customer customer){
		List<WhiteList> whiteList=whiteListDao.selectByParam(new WhiteList(customer.getCellPhone()));
		if(whiteList!=null&&whiteList.size()>0){
			WhiteList w = whiteList.get(0);
			//保存收入信息
			CustomerIncome customerIncome = new CustomerIncome();
			customerIncome.setCustomerID(customer.getCustomerID());
			customerIncome.setIncomeType("SALARY");
			customerIncome.setIncomeAmount(Float.valueOf(w.getIncome()));
			customerIncome.setIncomeCurrency("1");
			customerIncome.setTermType("M");
			customerIncomeDao.insertSelective(customerIncome);
			//更新客户信息
			customer.setAddress(w.getAddress());
			this.saveOrUpCustomer(request, customer);
		}
	}
	/**
	* @Title: doSupplementCustomer 
	* @Description: 实名认证
	* @param @param request
	* @param @param customer    设定文件 
	* @return void    返回类型 
	 */
	public void doSupplementCustomer(HttpServletRequest request,Customer customer){
		Customer c=(Customer)request.getSession().getAttribute("customer");
		if(c==null){
			throw new CustomException("会话消失");
		}else{
			customer.setCustomerID(c.getCustomerID());
		}
		//用户身份证信息已存在 则不采集数据
		Map m = customerDao.selectObject(c.getCellPhone());
		if(m==null){
//			customer.setCustomerStatus("PENDINZX");//等待失信检查
			customer.setCustomerStatus("BASIC");//实名认证
			saveOrUpCustomer(request,customer); //更新姓名
			
			customerCertificateService.saveCustomerCertificate(request,customer);//更新身份证
			customerEducationService.saveOrUpCustomerEducation(request, customer);//更新学历
			customerPersonalService.saveCustomerPersonal(request, customer);//更新婚姻情况
			this.saveCard(request, customer);
		}
	}
	/**
	 * @Title: doSupplementCustomerPersonal 
	 * @Description: 信息完善
	 * @author caiZhen
	 * @date 2016年6月7日 下午2:24:45
	 * @param @param request
	 * @param @param customer    设定文件 
	 * @return void    返回类型 
	 */
	public JSONObject doSupplementCustomer(HttpServletRequest request){
		Customer c=(Customer)request.getSession().getAttribute("customer");
		if(c==null){
			throw new CustomException("会话消失");
		}
		customerWorkexperienceService.saveWorkexperience(request, c);//更新工作经历
		
		
		Customer customer = new Customer();
		customer.setCustomerID(c.getCustomerID());
		customer.setEmail(request.getParameter("email"));
		
		customer.setCustomerStatus("PENDINZX");//等待执行检查
		saveOrUpCustomer(request,customer); //更新邮箱
		
		customerCertificateService.saveCustomerCertificate(request,customer);//更新QQ
		JSONObject jObject = new JSONObject();
		jObject.put("account", c.getCellPhone());
		jObject.put("is_get_juxinli_data", is_get_juxinli_data);
		return jObject;
	}
	/**
	 *
	 * @Title: getCustomer
	 * @Description: 根据客户信息获取客户列表
	 * @author caizhen
	 * @param @param customer
	 * @return List<Customer>    返回类型
	 */
	public List<Customer> getCustomer(Customer customer){
		return customerDao.getCustomer(customer);
	}
	public void collect_info(Customer customer){
		List<CustomerCertificate> ccList = customerCertificateService.selectByCustomerCertificate(new CustomerCertificate(customer.getCustomerID(),"ID",null));
		if(ccList==null||ccList.size()==0){
			throw new CustomException("该用户身份证信息为空");
		}
		Map<String, String> params = new HashMap<String, String>();  
//		params.put("name", customer.getName()); 
		params.put("idNo", ccList.get(0).getCertificateCode());
		params.put("resonCd", "01"); 
//		params.put("mobileNo", customer.getCellPhone());
//		params.put("cardCode", cardCode);
//		params.put("edu", highestDegree);
//		params.put("company", "");
		      
		HttpXmlClient.post("http://"+server_ip+"/captureOL/company_executeAuth.action", params);
	}
	/**
	 * @Title: saveCard 
	 * @Description: 保存账户信息
	 * @author caiZhen
	 * @date 2016年6月7日 下午4:27:16
	 * @param @param request
	 * @param @param customer    设定文件 
	 * @return void    返回类型 
	 */
	public void saveCard(HttpServletRequest request,Customer customer){
		Account account = new Account();
		account.setCustomerID(customer.getCustomerID());
		this.saveOrUpAccount(account);
		
		Card card = new Card();
		card.setAccountID(account.getAccountID());
		card.setCardCode(request.getParameter("cardCode"));
		this.saveOrUpCard(card);
	}
	/**
	 * 更新账户信息
	 * @param account
	 */
	public void saveOrUpAccount(Account account){
		List<Account> accountList = accountDao.selectByCustomerID(account.getCustomerID());
		if(accountList!=null&&accountList.size()>0){
			Account record = accountList.get(0);
			account.setAccountID(record.getAccountID());
			accountDao.updateByPrimaryKeySelective(account);
		}else{
			accountDao.insertSelective(account);
		}
	}
	public void saveOrUpCard(Card card){
		List<Card> cardList = cardDao.selectByAccountID(card.getAccountID());
		if(cardList!=null&&cardList.size()>0){
			Card record = cardList.get(0);
			card.setCardID(record.getCardID());
			cardDao.updateByPrimaryKeySelective(card);
		}else{
			cardDao.insertSelective(card);
		}
	}
//	public String collect_info2(HttpServletRequest request,Customer customer){
//		Map<String, String> param = new HashMap<String, String>();
//		param.put("name", customer.getName());
//		param.put("idNo", request.getParameter("idCard"));
//		param.put("resonCd", "01");
//		param.put("mobileNo", customer.getCellPhone());
//		
//		List<RequestHead> requestHeads = new ArrayList<RequestHead>();
//		requestHeads.add(new RequestHead("Content-Type", "application/json"));
//		try {
//			String json =HttpConnect.getJson("http://127.0.0.1:8080/captureOL/company_executeAuth.action?resonCd=01&name="+customer.getName()
//					+"&idNo="+request.getParameter("idCard")+"&mobileNo="+customer.getCellPhone(),
//					param, requestHeads,"post");
//			System.out.print("collect_info-----------------------------------"+json);
//			if (!"".equals(json)) {
//				JSONObject jsonObject = JSONObject.fromObject(json);
//				if("true".equals(jsonObject.getString("success"))){
//					
//				}else{
//					
//				}
//			}
//		} catch (Exception e) {
//			log.error(e.getMessage());
//		}
//		return null;
//	}
	/**
	 * @Title: doExecuteJxl 
	 * @Description: 根据手机号、服务码获取信息
	 * @author caiZhen
	 * @date 2016年6月13日 下午4:27:16
	 * @param @param request
	 * @param @param customer    设定文件 
	 * @return void    返回类型 
	 */
	public Map doExecuteJxl(HttpServletRequest request){
		Object o = customerDao.selectObject(request.getParameter("account"));
		if(o==null){
			throw new CustomException("未查到相关结果");
		}
		JSONObject jObject = JSONObject.fromObject(o);
		Map<String, String> params = new HashMap<String, String>();  
//			name, idNo, mobileNo, password, "", "", ""
//			name, idNo, mobileNo, password, token, website, captcha
		params.put("name",  jObject.getString("Name")); 
		params.put("mobileNo",  jObject.getString("CellPhone")); 
		params.put("idNo", jObject.getString("CertificateCode"));
		params.put("password",  request.getParameter("password"));
		params.put("token",  "");
		params.put("website",  "");
		params.put("captcha",  "");
		String response = HttpXmlClient.post("http://"+server_ip+"/captureOL/company_executeJxl.action", params);
		log.info("doExecuteJxl info"+response);
		if(response==null){
			throw new CustomException("未查到相关结果");
		}
		jObject=JSONObject.fromObject(response);
		if("true".equals(jObject.getString("success"))){
			params.put("token",  jObject.getString("token"));
			params.put("website",  jObject.getString("website"));
			params.put("captcha",  jObject.getString("captcha"));//验证码
		}				
		return params;
		
		
			
	}
	/**
	 * @Title: doValidateCode 
	 * @Description: 验证手机验证码
	 * @author caiZhen
	 * @date 2016年6月13日 下午4:27:16
	 * @param @param request
	 * @param @param customer    设定文件 
	 * @return void    返回类型 
	 */
	public void doValidateCode(HttpServletRequest request){
		List<Customer> customerList = customerDao.getCustomer(new Customer(request.getParameter("mobileNo")));
		if(customerList==null||customerList.size()<=0){
			throw new CustomException("无该用户");
		}
		Map<String, String> params = new HashMap<String, String>();  
			
			
		params.put("name",  request.getParameter("name")); 
		params.put("idNo", request.getParameter("idNo"));
		params.put("mobileNo",  request.getParameter("mobileNo")); 
		params.put("password",  request.getParameter("password"));
		params.put("token",  request.getParameter("token"));
		params.put("website",  request.getParameter("website"));
		params.put("captcha",  request.getParameter("captcha"));
		String response= HttpXmlClient.post("http://"+server_ip+"/captureOL/company_executeJxl.action", params);
		log.info("doValidateCode info"+response);
			
	}
	/**
	 * @Title: getValidateCode 
	 * @Description: 获取服务码验证码
	 * @author caiZhen
	 * @date 2016年6月17日 下午4:27:16
	 * @param @param request
	 * @param @param customer    设定文件 
	 * @return void    返回类型 
	 */
	public String getValidateCode(HttpServletRequest request){
		Map map = customerDao.selectObject(request.getParameter("cellPhone"));
		if(map==null){
			throw new CustomException("用户信息不存在");
		}
		Map<String, String> params = new HashMap<String, String>();  
		params.put("token",  null);
		params.put("name",  map.get("Name").toString()); 
		params.put("idNo", map.get("CertificateCode").toString());
		params.put("mobileNo", map.get("CellPhone").toString()); 
		params.put("password",  null);
		params.put("captcha",  null);
		params.put("website",  null);
//		String response= HttpXmlClient.post("http://139.196.136.32/captureOL/company_resetPassword.action", params);
		String response="{\"success\":true,\"token\":1,\"website\":2}";//  process_code =10002 表示短信已经成功发送。
		if(response==null){
			throw new CustomException("验证码发送失败");
		}
		JSONObject jObject = JSONObject.fromObject(response);
		if(jObject!=null&&"true".equals(jObject.getString("success"))){
			map.put("token", jObject.getString("token"));
			map.put("website", jObject.getString("website"));
			
			StringUtil.setSession(request, map, "validateCodeSession");
				return "验证码发送成功";
		}else{
			throw new CustomException("验证码发送失败");
		}
	}
	/**
	 * @Title: doServerCode 
	 * @Description: 设置服务码
	 * @author caiZhen
	 * @date 2016年6月17日 下午4:27:16
	 * @param @param request
	 * @param @param customer    设定文件 
	 * @return void    返回类型 
	 */
	public String doSetServerCode(HttpServletRequest request){
//		List<Customer> customerList = customerDao.getCustomer(new Customer(request.getParameter("mobileNo")));
//		if(customerList==null||customerList.size()<=0){
//			throw new CustomException("无该用户");
//		}
		Object o = StringUtil.getSession(request, "validateCodeSession");
		if(o==null){
			throw new CustomException("会话结束,请重新设置");
		}
		Map map =  (Map)o;
		Map<String, String> params = new HashMap<String, String>();  
		params.put("token", map.get("token").toString());
		params.put("website", map.get("website").toString());
		
		params.put("name", map.get("Name").toString()); 
		params.put("idNo", map.get("CertificateCode").toString());
		params.put("mobileNo", map.get("CellPhone").toString()); 
		
		params.put("password", request.getParameter("serviceCode"));
		params.put("captcha", request.getParameter("validateCode"));
//		String response= HttpXmlClient.post("http://139.196.136.32/captureOL/company_resetPassword.action", params);
		String response= "{\"success\":\"true\",data:{\"process_code\":\"11000\",\"content\":\"设置成功\"}}";// 密码重置成功判断字段。process_code为110000 其他都认为是失败
		if(response==null){
			throw new CustomException("服务密码重置失败");
		}
		JSONObject jObject = JSONObject.fromObject(response);
		if(jObject!=null&&"true".equals(jObject.getString("success"))){
			jObject = jObject.getJSONObject("data");
			if(jObject!=null&&"11000".equals(jObject.getString("process_code"))){
				return "服务密码重置成功";
			}else{
				throw new CustomException("服务密码重置失败");
			}
		}else{
			throw new CustomException("服务密码重置失败");
		}				
	}
	/**
	 * @Title: saveCustomerContactor 
	 * @Description: 保存用户常用收货地址信息
	 * @author caiZhen
	 * @date 2016年7月2日 下午4:27:16
	 * @param @param request
	 * @return void    返回类型 
	 */
	public void saveCustomerContactor(HttpServletRequest request){
		String ccStr = request.getParameter("ccList");
		JSONArray jArray= JSONArray.fromObject(ccStr);
		System.out.println(jArray);
		CustomerContactor cc=null;
		if(jArray!=null){
			for(int i=0;i<jArray.size();i++){
				cc = (CustomerContactor) JSONObject.toBean(jArray.getJSONObject(i), CustomerContactor.class);
			    customerContactorDao.insertSelective(cc);
			}
		}
	}
	/**
	 * @Title: doUserLogin 
	 * @Description: 用户登陆
	 * @author caiZhen
	 * @date 2016年7月13日 下午2:27:16
	 * @param @param request
	 * @return Customer 
	 */
	public JSONObject doUserLogin(HttpServletRequest request){
		JSONObject jObject = new JSONObject();
		Customer customer = new Customer(request.getParameter("cellPhone"));
		//接收登陆验证码的手机
		String loginPhone = (String)StringUtil.getSession(request, "loginPhone");
		//发送的验证码信息
		String loginVerificationCode = (String)StringUtil.getSession(request, "loginVerificationCode");
		
		if(StringUtils.isBlank(loginVerificationCode)||StringUtils.isBlank(loginPhone)){
			throw new CustomException("验证码已过期,请重置");
		}
		if(!loginPhone.equals(customer.getCellPhone())){
			throw new CustomException("您输入手机号与收验证码手机号不一致");
		}
		//用户输入的验证码
		String loginCode=request.getParameter("loginCode");
		if(!loginVerificationCode.equals(loginCode)){
			throw new CustomException("请核对验证码");
		}
		List<Customer> customerList = customerDao.getCustomer(customer);
		if(customerList==null||customerList.size()==0){
			throw new CustomException("该用户无借款信息");
		}
		customer = customerList.get(0);
		StringUtil.setSession(request, customer, "customer");
		jObject.put("cellPhone", customer.getCellPhone());
		jObject.put("customerID", customer.getCustomerID());
		jObject.put("name", customer.getName());
		
		if(StringUtils.isNotBlank(customer.getCustomerStatus())){
			String customerStatus = redirectbyCustomerStatus(customer.getCustomerStatus());
			if(StringUtils.isNotBlank(customerStatus)){
				jObject.put("customerStatus", customerStatus);
			}
		}
		return jObject;
	}
	/**
	 * @Title: getMenberCenter 
	 * @Description: 获取个人信息
	 * @author caiZhen
	 * @date 2016年7月13日 下午2:27:16
	 * @param @param request
	 * @return Map<String,String>
	 */
	public Map<String,String> getMenberCenter(HttpServletRequest request){
		Customer c=(Customer)request.getSession().getAttribute("customer");
		if(c==null){
			throw new CustomException("会话消失");
		}
		Map<String,String> map=customerDao.getMenberCenter(c);
		return map;
	}
	/**
	 * @Title: redirectbyCustomerStatus 
	 * @Description: 根据customerStatus进入相应的界面
	 * 				 DRAFT:进入实名认证界面
	 * 			     BASIC:进入信息完善界面
	 * 				 other:进入个人中心
	 * @author caiZhen
	 * @date 2016年7月14日 下午3:46:16
	 * @param @param CustomerStatus
	 * @return String
	 */
	public String redirectbyCustomerStatus(String customerStatus){
		if("DRAFT".equals(customerStatus)||"BASIC".equals(customerStatus)){
			return customerStatus;
		}else
			return "menberCenter";
	}
}
