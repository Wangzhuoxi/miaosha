package com.NEU.controller;

import com.NEU.controller.viewobject.ItemVO;
import com.NEU.error.BusinessException;
import com.NEU.response.CommonReturnType;
import com.NEU.service.CacheService;
import com.NEU.service.ItemService;
import com.NEU.service.PromoService;
import com.NEU.service.model.ItemModel;
import net.sf.json.JSONObject;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 取本地缓存，redis，最后数据库
 */
@Controller("item")
@RequestMapping("/item")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class ItemController {
    @Autowired
    private ItemService itemService;
    @Autowired
    private RedisTemplate<String, ItemModel> redisTemplate;
    @Autowired
    CacheService cacheService;
    @Autowired
    PromoService promoService;
    //创建商品
    @RequestMapping(value = "/create", method = {RequestMethod.POST}, consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType createItem(@RequestParam(name = "title") String title,
                                       @RequestParam(name = "price") BigDecimal price,
                                       @RequestParam(name = "stock") Integer stock,
                                       @RequestParam(name = "imgUrl") String imgUrl,
                                       @RequestParam(name = "discription") String discription) throws BusinessException {
        // 封装service请求用来创建商品
        ItemModel itemModel = new ItemModel();
        itemModel.setTitle(title);
        itemModel.setPrice(price);
        itemModel.setStock(stock);
        itemModel.setImgUrl(imgUrl);
        itemModel.setDiscription(discription);

        ItemModel itemModelForReturn = itemService.createItem(itemModel);
        ItemVO itemVO = this.convertVOFromModel(itemModelForReturn);

        return CommonReturnType.create(itemVO);
    }
    private ItemVO convertVOFromModel(ItemModel itemModel) {
        if(itemModel == null) {
            return null;
        }
        ItemVO itemVO = new ItemVO();
        BeanUtils.copyProperties(itemModel,itemVO);
        return itemVO;
    }
    //商品详情页浏览
    @RequestMapping(value = "/get", method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType getItem(@RequestParam(name = "id")Integer id){

        ItemModel itemModel=null;
        itemModel=(ItemModel)cacheService.getFromCommonCache("item_"+id);

        if(itemModel==null) {
            //根据商品的id到redis内获取
            Object obj = redisTemplate.opsForValue().get("item_" + id);
            JSONObject json = JSONObject.fromObject(obj);
            itemModel = (ItemModel) JSONObject.toBean(json, ItemModel.class);
            //如果redis内妹有，则访问下游service,并放入redis
            if (itemModel == null) {
                itemModel = itemService.getItemById(id);
                redisTemplate.opsForValue().set("item_" + id, itemModel);
                //10minute key的失效时间
                redisTemplate.expire("item_" + id, 10, TimeUnit.MINUTES);
            }
            cacheService.setCommonCache("item_" + id, itemModel);
        }
        ItemVO itemVO = this.convertVOFromModel(itemModel);
        if(itemModel.getPromoModel() != null){
            //有正在进行或即将进行的秒杀活动
            itemVO.setPromoStatus(itemModel.getPromoModel().getStatus());
            itemVO.setPromoId(itemModel.getPromoModel().getId());
            //把DateTime转化为String类型传到前端，避免数据格式问题(json序列化问题)
            itemVO.setStartDate(itemModel.getPromoModel().getStartDate().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
            itemVO.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
        }
        //填充本地缓存

        else {
            itemVO.setPromoStatus(0);
        }
        return CommonReturnType.create(itemVO);
    }

    @RequestMapping(value = "/list", method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType listItem(){
        List<ItemModel> itemModelList=itemService.listItem();
        List<ItemVO> itemVOList=itemModelList.stream().map(itemModel->{
            ItemVO itemVO=convertVOFromModel(itemModel);
            return itemVO;
                }).collect(Collectors.toList());

        return CommonReturnType.create(itemModelList);
    }

    @RequestMapping(value = "/publishpromo", method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType publishpromo(@RequestParam(name="id")Integer id){
        promoService.publishPromo(id);
        return CommonReturnType.create(null);

    }

}
