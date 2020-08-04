package com.NEU.service;

import com.NEU.error.BusinessException;
import com.NEU.service.model.ItemModel;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.io.UnsupportedEncodingException;
import java.util.List;

public interface ItemService {
    //创建商品
    ItemModel createItem(ItemModel itemModel) throws BusinessException;

    //商品列表浏览
    List<ItemModel> listItem();

    //商品详细浏览
    ItemModel getItemById(Integer id);

    //库存扣减
    boolean decreaseStock(Integer itemId,Integer amount) throws BusinessException, InterruptedException, RemotingException, MQClientException, MQBrokerException, UnsupportedEncodingException;


    //商品下单后对应销量增加
    void increaseSales(Integer itemId,Integer amount);

    //优化：放入redis中校验：item和promo model的缓存模型（是否有效）
    ItemModel getItemByIdInCache(Integer id);
    //异步更新库存
    boolean asyncDecreaseStock(Integer itemId,Integer amount) throws UnsupportedEncodingException;
//库存回滚
boolean increaseStock(Integer itemId,Integer amount) throws BusinessException ;

//初始化库存流水:使得实务中有stocklog生成，当有stocklog记录时，追踪数据，追踪下单状态是成功还是失败
    String initStockLog(Integer itemId,Integer amount);
}
