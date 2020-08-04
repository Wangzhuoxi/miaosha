package com.NEU.service.impl;

import com.NEU.dao.PromoDOMapper;
import com.NEU.dataobject.PromoDO;
import com.NEU.error.BusinessException;
import com.NEU.error.EmBusinessError;
import com.NEU.response.CommonReturnType;
import com.NEU.service.ItemService;
import com.NEU.service.PromoService;
import com.NEU.service.UserService;
import com.NEU.service.model.ItemModel;
import com.NEU.service.model.PromoModel;
import com.NEU.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PromoServiceImpl implements PromoService {
    @Autowired
    private PromoDOMapper promoDOMapper;
    @Autowired
    private ItemService itemService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private UserService userService;
    /**
     *
     * @param itemId
     * @return
     */
    @Override
  public PromoModel getPromoByItemId(Integer itemId){

        PromoDO promoDO=promoDOMapper.selectByItemId(itemId);
        //dataobject->model
        PromoModel promoModel=convertFromDataObject(promoDO);
        if(promoModel==null)
            return null;

        //判断当前时间是否秒杀活动即将开始或者正在进行

        //开始时间还未开始
        if(promoModel.getStartDate().isAfterNow())
        {
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow())
            promoModel.setStatus(3);
        else{
            promoModel.setStatus(2);
        }

        return promoModel;
    }

    @Override
    public void publishPromo(Integer promoId) {
        //获取秒杀活动，看是否存在
        PromoDO promoDO=promoDOMapper.selectByPrimaryKey(promoId);
        if(promoDO.getItemId()==null||promoDO.getItemId().intValue()==0){
            return;
        }
        ItemModel itemModel=itemService.getItemById(promoDO.getItemId());
        //将库存同步到redis内：秒杀商品的id和库存（活动-》找item id-》找item信息 -》找库存
        redisTemplate.opsForValue().set("promo_item_stock_"+itemModel.getId(),itemModel.getStock());
        //商品秒杀大闸放入redis中
        redisTemplate.opsForValue().set("promo_door_count_"+promoId,itemModel.getStock()*5);

    }

    @Override
    public String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) {
        //判断是否库存已售罄，若对应的售罄key存在，则直接返回下单失败
        if(redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)){
            return null;
        }
        PromoDO promoDO=promoDOMapper.selectByPrimaryKey(promoId);
        //dataobject->model
        PromoModel promoModel=convertFromDataObject(promoDO);
        if(promoModel==null)
            return null;

        //判断当前时间是否秒杀活动即将开始或者正在进行
        if(promoModel.getStartDate().isAfterNow())
            promoModel.setStatus(1);
        else if (promoModel.getEndDate().isBeforeNow())
            promoModel.setStatus(3);
        else
            promoModel.setStatus(2);

        if(promoModel.getStatus()!=2)
            return null;
        //判断下单商品是否存在
        ItemModel itemModel=itemService.getItemByIdInCache(itemId);
        if(itemModel==null)
           return null;
        //判断用户是否存在
        UserModel userModel = userService.getUserByIdInCache(userId);
        if(userModel == null) {
            return null;
        }

        //获取秒杀大闸得数量，如果数量小于0也不能生成token
        long result=redisTemplate.opsForValue().increment("promo_door_count_"+promoId,-1);
        if(result<0)
            return null;
        //生成token并存入redis
        String token= UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue().set("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId,token);
        redisTemplate.expire("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId,5, TimeUnit.MINUTES);
        return token;


    }

    private PromoModel convertFromDataObject(PromoDO promoDO){
        if(promoDO == null) {
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
      promoModel.setEndDate(new DateTime(promoDO.getEndDate()));

        return promoModel;
    }

}
