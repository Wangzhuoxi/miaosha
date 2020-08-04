package com.NEU.controller;

import com.NEU.controller.viewobject.UserVO;
import com.NEU.error.BusinessException;
import com.NEU.error.EmBusinessError;
import com.NEU.response.CommonReturnType;
import com.NEU.service.UserService;
import com.NEU.service.model.UserModel;
import com.alibaba.druid.util.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller("user")
@RequestMapping("/user")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class UserController extends BaseController {
    @Autowired
    private UserService userService;
    @Autowired
    private HttpServletRequest httpServletRequest;
    @Autowired
    private RedisTemplate redisTemplate;

    @RequestMapping("/get")
    @ResponseBody
    public CommonReturnType getUser(@RequestParam("id")Integer id) throws BusinessException {
        //调用 service服务获取对应id的用户对象并返回给前端
        UserModel userModel=userService.getUserById(id);

        //被抛到了tomcat容器层，此时还是个异常，需要拦截
        if(userModel==null)
        {
            throw new BusinessException(EmBusinessError.USER_NOT_EXIT);
        }

        //将核心领域模型用户对象转化为可供ui使用 的viewobject
        UserVO userVO= concertFromModel(userModel);
        return CommonReturnType.create(userVO);
    }
    private UserVO concertFromModel(UserModel userModel)
    {
        if(userModel==null)
            return null;
        else {
            UserVO userVO= new UserVO();
            BeanUtils.copyProperties(userModel,userVO);
            return userVO;
        }
    }

    //用户获取otp短信接口
    @RequestMapping(value ="/getotp", method = {RequestMethod.POST}, consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType getOtp(@RequestParam(name = "telphone") String telphone) {
        //1. 需要按照一定的规则生成otp验证码
        Random random = new Random();
        int randomInt = random.nextInt(99999);
        randomInt += 10000;
        String otpCode = String.valueOf(randomInt);

        //2. 将otp验证码同对应用户的手机号关联，使用httpsession的方式绑定它的手机号与optCode
        httpServletRequest.getSession().setAttribute(telphone, otpCode);

        //3. 将otp验证码通过短信通道发送给用户，省略
        System.out.println("telphone = " + telphone + "& otpCode = " + otpCode);

        return CommonReturnType.create(null);
    }

    //用户注册接口


    @RequestMapping(value ="/register", method = {RequestMethod.POST}, consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType register(@RequestParam(name = "telphone")String telphone,
                                     @RequestParam(name = "otpCode")String otpCode,
                                     @RequestParam(name = "name")String name,
                                     @RequestParam(name = "gender")Integer gender, @RequestParam(name = "age")int age,
                                     @RequestParam(name = "password")String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        //验证手机号和otpCode码符合
       String inSessionOtpCode=(String)this.httpServletRequest.getSession().getAttribute(telphone);
       if(!StringUtils.equals(otpCode,inSessionOtpCode)){
           throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"短信验证码输入错误");
       }
        //用户注册流程
        UserModel userModel = new UserModel();
        userModel.setName(name);
        userModel.setTelphone(telphone);
        userModel.setGender(Byte.valueOf(String.valueOf(gender.intValue())));
        userModel.setAge(age);
        userModel.setRegisterMode("byphone");
        userModel.setEncrptPassword(this.encodeByMd5(password));
        userService.register(userModel);
        return CommonReturnType.create(null);

    }
    public String encodeByMd5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {

        //确定计算方法
        MessageDigest md5 = MessageDigest.getInstance("MD5");
      //  BASE64Encoder base64en = new BASE64Encoder();
        //加密字符串
      //  String newstr = base64en.encode(md5.digest(str.getBytes("utf-8")));
        return Base64.encodeBase64String(md5.digest(str.getBytes("utf-8")));
    }


    //用户登录接口

    @CrossOrigin(allowCredentials = "true",allowedHeaders = "*",origins = {"*"})
    @RequestMapping(value ="/login", method = {RequestMethod.POST}, consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType login(@RequestParam(name = "telphone") String telphone,
                                  @RequestParam(name = "password") String password
                               ) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        System.out.println("telephone is"+telphone);
        //入参校验
        if (org.apache.commons.lang3.StringUtils.isEmpty(telphone)) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        //用户登录服务，用来校验用户登录是否合法
        UserModel userModel = userService.validateLogin(telphone, this.encodeByMd5(password));

    /**    //将登录凭证加入到用户登录成功的session内
        httpServletRequest.getSession().setAttribute("IS_LOGIN", true);
        httpServletRequest.getSession().setAttribute("LOGIN_USER", userModel);
*/
    //修改为若用户登陆成功则将对应的登陆信息和登陆凭证一起存入redis中
        //1生成登陆凭证（token）
       String uuidToken= UUID.randomUUID().toString();
       uuidToken.replace("-","");

       //2建立token和的用户登录态之间的联系(如果token存在那么用户就是登陆过)
        redisTemplate.opsForValue().set(uuidToken,userModel);
          //设置超时时间为1h
        redisTemplate.expire(uuidToken,1, TimeUnit.HOURS);

        return CommonReturnType.create(uuidToken);
    }


}
