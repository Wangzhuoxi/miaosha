package com.NEU.service.impl;

import com.NEU.dao.ItemDOMapper;
import com.NEU.dao.ItemStockDOMapper;
import com.NEU.dao.PromoDOMapper;
import com.NEU.dao.StockLogDOMapper;
import com.NEU.dataobject.ItemDO;
import com.NEU.dataobject.ItemStockDO;
import com.NEU.dataobject.StockLogDO;
import com.NEU.error.BusinessException;
import com.NEU.error.EmBusinessError;
import com.NEU.mq.MqProducer;
import com.NEU.service.ItemService;
import com.NEU.service.PromoService;
import com.NEU.service.model.ItemModel;
import com.NEU.service.model.PromoModel;
import com.NEU.validator.ValidationResult;
import com.NEU.validator.ValidatorImpl;
import net.sf.json.JSONObject;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Transient;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private ValidatorImpl validator;
    @Autowired
    private ItemDOMapper itemDOMapper;
    @Autowired
    private ItemStockDOMapper itemStockDOMapper;
    @Autowired
    private PromoService promoService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private MqProducer mqProducer;
    @Autowired
    private StockLogDOMapper stockLogDOMapper;
    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        //校验入参
        ValidationResult result = validator.validate(itemModel);
        if (result.isHasErrors()) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }
        //转化itemmodel为dataobject
        ItemDO itemDO = this.convertItemDOFromItemModel(itemModel);
        //写入数据库(两个表)
itemDOMapper.insertSelective(itemDO);
        itemModel.setId(itemDO.getId());
        ItemStockDO itemStockDO = this.convertItemStockDOFromItemModel(itemModel);
        itemStockDOMapper.insertSelective(itemStockDO);
        //返回创建完成的对象
        return this.getItemById(itemModel.getId());
    }

    @Override
    public List<ItemModel> listItem() {

       List<ItemDO> itemDOList=itemDOMapper.listItem();
      List<ItemModel> listModelList=itemDOList.stream().map(itemDO -> {
          ItemStockDO itemStockDO=itemStockDOMapper.selectByItemId(itemDO.getId());
          ItemModel itemModel=convertModelFromDataObject(itemDO,itemStockDO);
          return itemModel;
      }).collect(Collectors.toList());

       return listModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);

        if(itemDO == null){
            return null;
        }

        //获取该商品库存数量
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());

        //将dataobject转化为model
        ItemModel itemModel = this.convertModelFromDataObject(itemDO,itemStockDO);
        //获取商品秒杀活动信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        if(promoModel != null && promoModel.getStatus() != 3) {
            itemModel.setPromoModel(promoModel);
        }

        return itemModel;

    }

    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException, InterruptedException, RemotingException, MQClientException, MQBrokerException, UnsupportedEncodingException {
        long result = redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount.intValue() * -1);
        if(result >0){
            //更新库存成功
            return true;
        }else if(result == 0){
            //打上库存已售罄的标识
            redisTemplate.opsForValue().set("promo_item_stock_invalid_"+itemId,"true");

            //更新库存成功
            return true;
        }else{
            //更新库存失败
            increaseStock(itemId,amount);
            return false;
        }
    }

    private ItemDO convertItemDOFromItemModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel, itemDO);
        itemDO.setPrice(itemModel.getPrice().doubleValue());
        return itemDO;
    }
    private ItemStockDO convertItemStockDOFromItemModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());
        return itemStockDO;
    }
    private ItemModel convertModelFromDataObject(ItemDO itemDO,ItemStockDO itemStockDO){
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO,itemModel);
        itemModel.setStock(itemStockDO.getStock());
        itemModel.setPrice(new BigDecimal(itemDO.getPrice()));

        return itemModel;
    }

    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) {
        itemDOMapper.increaseSales(itemId,amount);
    }

    @Override
    public ItemModel getItemByIdInCache(Integer id) {

        //(ItemModel)redisTemplate.opsForValue().get("item_validate_"+id);
        Object obj = redisTemplate.opsForValue().get("item_validate_" + id);
        JSONObject json = JSONObject.fromObject(obj);
      ItemModel  itemModel = (ItemModel) JSONObject.toBean(json, ItemModel.class);
        if(itemModel==null){
            itemModel=this.getItemById(id);

            redisTemplate.opsForValue().set("item_validate_"+id,itemModel);
            redisTemplate.expire("item_validate_"+id,10, TimeUnit.MINUTES);
        }
        return itemModel;
    }

    @Override
    public boolean asyncDecreaseStock(Integer itemId, Integer amount) throws UnsupportedEncodingException {
        boolean mqResult = mqProducer.asyncReduceStock(itemId, amount);
        return mqResult;
    }

    @Override
    public boolean increaseStock(Integer itemId, Integer amount) throws BusinessException {
        redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount.intValue());
        return true;
    }

    //初始化对应的库存流水
    @Override
    @Transactional
    public String initStockLog(Integer itemId, Integer amount) {
       StockLogDO stockLogDO=new StockLogDO();
       stockLogDO.setItemId(itemId);
       stockLogDO.setAmount(amount);
       stockLogDO.setStockLogId(UUID.randomUUID().toString().replace("-",""));
       stockLogDO.setStatus(1);
      stockLogDOMapper.insert(stockLogDO);
      return stockLogDO.getStockLogId();

    }
}
