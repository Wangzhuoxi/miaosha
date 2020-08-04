package com.NEU.mq;

import com.NEU.dao.StockLogDOMapper;
import com.NEU.dataobject.StockLogDO;
import com.NEU.error.BusinessException;
import com.NEU.service.OrderService;
import com.alibaba.fastjson.JSON;

import io.lettuce.core.TransactionResult;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Component
public class MqProducer {
    /**
     * bean初始化完成后调用
     */
    private DefaultMQProducer producer;
    private TransactionMQProducer transactionMQProducer;
    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    @Value("${mq.topicname}")
    private String topicName;
    @Autowired
    private OrderService orderService;
    @Autowired
    private StockLogDOMapper stockLogDOMapper;
    @PostConstruct
    public void init() throws MQClientException {
        //初始化producer的group名字、和地址。
        producer = new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(nameAddr);
        producer.start();
        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();
        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object arg) {
                Integer itemId=(Integer)((Map)arg).get("itemId");
                Integer promoId=(Integer)((Map)arg).get("promoId");
                Integer userId=(Integer)((Map)arg).get("userId");
                Integer amount=(Integer)((Map)arg).get("amount");
                String stockLogId=(String)((Map)arg).get("stockLogId");

                try {
                    //真正要做的事：创建订单
                    //失败则回滚,设置stocklog为回滚状态
                    orderService.createOrder(userId, itemId, promoId, amount,stockLogId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                } catch (RemotingException e) {
                    e.printStackTrace();
                    StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                } catch (UnsupportedEncodingException e) {
                    StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    e.printStackTrace();
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                } catch (MQClientException e) {
                    StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    e.printStackTrace();
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                } catch (MQBrokerException e) {
                    StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    e.printStackTrace();
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;

            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                //根据是否扣减库存成功，来判断要返回commot rollba还是unknown
                String jsonString=new String(msg.getBody());
                Map<String,Object>map= JSON.parseObject(jsonString,Map.class);
                Integer itemId=(Integer)map.get("itemId");
                Integer amount=(Integer)map.get("amount");
                String stockLogId=(String)map.get("stockLogId");
                StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
                if(stockLogDO==null)
                    return LocalTransactionState.UNKNOW;
                if(stockLogDO.getStatus().intValue()==2)
                    return LocalTransactionState.COMMIT_MESSAGE;
                else  if(stockLogDO.getStatus().intValue()==1)
                    return LocalTransactionState.UNKNOW;
                else
                    return LocalTransactionState.ROLLBACK_MESSAGE;

            }
        });
    }
    //事务型同步库存扣减消息
    public boolean transactionAsyncReduceStock(Integer userId,Integer itemId,Integer promoId,Integer amount,String stockLogId) throws UnsupportedEncodingException {
        Map<String,Object> bodyMap=new HashMap<>();
        bodyMap.put("itemId",itemId);
        bodyMap.put("amount",amount);
        bodyMap.put("stockLogId",stockLogId);
        Map<String,Object> argsMap=new HashMap<>();
        argsMap.put("itemId",itemId);
        argsMap.put("amount",amount);
        argsMap.put("userId",userId);
        argsMap.put("promoId",promoId);
        argsMap.put("stockLogId",stockLogId);
        System.out.println("同步消息"+amount+"item_id is"+itemId);
        Message message=new Message(topicName,"increase",JSON.toJSON(bodyMap).toString().getBytes("UTF-8"));
        TransactionSendResult sendResult=null;
        try{
        sendResult=    transactionMQProducer.sendMessageInTransaction(message,argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }
        if(sendResult.getLocalTransactionState()==LocalTransactionState.COMMIT_MESSAGE)
            return true;
        else if(sendResult.getLocalTransactionState()==LocalTransactionState.ROLLBACK_MESSAGE)
            return false;
        else
            return false;
    }
    /**
     * 同步库存扣减消息
     */
    public boolean asyncReduceStock(Integer itemId, Integer amount) throws UnsupportedEncodingException {
        System.out.println(nameAddr);
        Map<String,Object> bodyMap=new HashMap<>();
        bodyMap.put("itemId",itemId);
        bodyMap.put("amount",amount);
        System.out.println("同步消息"+amount+"item_id is"+itemId);
        Message message=new Message(topicName,"increase",JSON.toJSON(bodyMap).toString().getBytes("UTF-8"));
  try{
      producer.send(message);
  } catch (MQClientException e) {
      e.printStackTrace();
      return false;
  } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
  } catch (RemotingException e) {
      e.printStackTrace();
      return false;
  } catch (MQBrokerException e) {
      e.printStackTrace();
      return false;
  }
return true;
    }



}
