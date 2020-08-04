package com.NEU.controller;

import com.NEU.dataobject.ItemStockDO;
import com.NEU.error.BusinessException;
import com.NEU.error.EmBusinessError;
import com.NEU.mq.MqProducer;
import com.NEU.response.CommonReturnType;
import com.NEU.service.ItemService;
import com.NEU.service.OrderService;
import com.NEU.service.PromoService;
import com.NEU.service.model.OrderModel;
import com.NEU.service.model.UserModel;
import com.NEU.util.CodeUtil;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 发送事务型消息方式驱动下单，根据对应的回调状态决定消息发送还是rollback
 */
@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class OrderController extends BaseController{
    @Autowired
    private OrderService orderService;
    @Autowired
    private HttpServletRequest httpServletRequest;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private MqProducer mqProducer;
    @Autowired
    private ItemService itemService;
   @Autowired
   private PromoService promoService;

   private ExecutorService executorService;

   //20个线程得线程池
@PostConstruct
public void init(){
    executorService= Executors.newFixedThreadPool(20);

}
    //生成验证码
    @RequestMapping(value = "/generateverifycode",method = {RequestMethod.GET,RequestMethod.POST})
    @ResponseBody
    public void generateverifycode(HttpServletResponse response) throws BusinessException, IOException {
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(org.springframework.util.StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能生成验证码");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能生成验证码");
        }

        Map<String,Object> map = CodeUtil.generateCodeAndPic();

        redisTemplate.opsForValue().set("verify_code_"+userModel.getId(),map.get("code"));
        redisTemplate.expire("verify_code_"+userModel.getId(),10,TimeUnit.MINUTES);

        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());


    }
    //生成秒杀令牌
    @RequestMapping(value = "/generatetoken", method = {RequestMethod.POST}, consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType generatetoken(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name="promoId",required = false)Integer promoId,
                                          @RequestParam(name="verifyCode")String verifyCode
                                      ) throws BusinessException {

        String token=httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆不能下单");
        }
        //获取用户登录信息
        Object obj=redisTemplate.opsForValue().get(token);
        JSONObject json = JSONObject.fromObject(obj);
        UserModel userModel=(UserModel)JSONObject.toBean(json, UserModel.class);

        if(userModel==null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆不能下单");
        }

        //获取秒杀访问令牌
        String promoToken=promoService.generateSecondKillToken(promoId,itemId,userModel.getId());
        if(promoToken==null)
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"令牌生成失败");
        return CommonReturnType.create(promoToken);

    }


    //封装下单请求
    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name="promoId",required = false)Integer promoId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoToken")String promoToken) throws BusinessException, InterruptedException, RemotingException, MQClientException, MQBrokerException, UnsupportedEncodingException, ExecutionException {



        String token=httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆不能下单");
        }

        //获取用户登录信息

        Object obj=redisTemplate.opsForValue().get(token);
        JSONObject json = JSONObject.fromObject(obj);
        UserModel userModel=(UserModel)JSONObject.toBean(json, UserModel.class);

        if(userModel==null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆不能下单");
        }

        if(promoId!=null)
        {
            String inRedisPromoToken=(String)redisTemplate.opsForValue().get("promo_token_"+promoId+"_userid_"+userModel.getId()+"_itemid_"+itemId);
            if(inRedisPromoToken==null){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀令牌校验失败");
            }
            if(!org.apache.commons.lang3.StringUtils.equals(promoToken,inRedisPromoToken)){
                throw  new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀校验失败");
            }
        }
//同步调用线程池得submit方法，
        //拥塞窗口为20得等待队列， 用来队列化泄洪，也就是同时支持20个用户下单
        Future<Object> future=executorService.submit(new Callable<Object>() {
       @Override
     public Object call() throws Exception {
           String stockLogId=itemService.initStockLog(itemId,amount);
           if(!mqProducer.transactionAsyncReduceStock(userModel.getId(),itemId,promoId,amount,stockLogId)){
               throw new BusinessException(EmBusinessError.UNKNOW_ERROR,"下单失败");
           }
           return null;
       }
   });
        future.get();
        //加入库存流水init的状态：在下单之前
        //再去完成对应的下单事务型消息机制

        return CommonReturnType.create(null);
    }


}
