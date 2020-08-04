package com.NEU.service.impl;

import com.NEU.dao.OrderDOMapper;
import com.NEU.dao.SequenceDOMapper;
import com.NEU.dao.StockLogDOMapper;
import com.NEU.dataobject.OrderDO;
import com.NEU.dataobject.SequenceDO;
import com.NEU.dataobject.StockLogDO;
import com.NEU.error.BusinessException;
import com.NEU.error.EmBusinessError;
import com.NEU.service.ItemService;
import com.NEU.service.OrderService;
import com.NEU.service.UserService;
import com.NEU.service.model.ItemModel;
import com.NEU.service.model.OrderModel;
import com.NEU.service.model.UserModel;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;
    @Autowired
    private OrderDOMapper orderDOMapper;
    @Autowired
    private SequenceDOMapper sequenceDOMapper;
    @Autowired
    private StockLogDOMapper stockLogDOMapper;
    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId,Integer amount,String stockLogId) throws BusinessException, InterruptedException, RemotingException, UnsupportedEncodingException, MQClientException, MQBrokerException {
        //1.校验下单状态（下单商品是否存在，用户是否合法，购买数量是否正确）
        ItemModel itemModel=itemService.getItemByIdInCache(itemId);
        if(itemModel==null)
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"产品信息不存在");

        if(amount <= 0 || amount > 99){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"数量信息不正确");
        }

        //2.落单减库存
        boolean result=itemService.decreaseStock(itemId,amount);
        if(!result)
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);

        //3.订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if(promoId != null) {
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        } else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(BigDecimal.valueOf(amount)));
        //生成交易订单号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);
        //加商品的销量
        itemService.increaseSales(itemId,amount);
        //设置库存流水状态为成功
        StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if(stockLogDO==null)
            throw new BusinessException(EmBusinessError.UNKNOW_ERROR);
         stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
         //返回前端
        return orderModel;
    }
    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if(orderModel == null){
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel,orderDO);
        return orderDO;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private String generateOrderNo()
    {
        StringBuffer sb=new StringBuffer();
        //订单16wei，前8wei时间信息，后六位
        LocalDateTime now=LocalDateTime.now();
       String nowDate= now.format(DateTimeFormatter.ISO_DATE).replace("-","");

        System.out.println(nowDate);
        sb.append(nowDate);

        //中间六位为自增序列
        int sequence=0;
        SequenceDO sequenceDO=sequenceDOMapper.getSequenceByName("order_info");
        sequence=sequenceDO.getCurrentValue();


        System.out.println(sequence);


        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue()+sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        String sequenceStr=String.valueOf(sequence);
        for(int i=0;i<6-sequenceStr.length();i++)
            sb.append(0);
        sb.append(sequenceStr);
        sb.append("00");
        System.out.println(sb.toString());
        return sb.toString();

    }
}
