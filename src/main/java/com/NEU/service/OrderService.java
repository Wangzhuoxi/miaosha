package com.NEU.service;

import com.NEU.error.BusinessException;
import com.NEU.service.model.OrderModel;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.io.UnsupportedEncodingException;

public interface OrderService {
    OrderModel createOrder(Integer userId,Integer itemId,Integer promId,Integer amount,String stockLogId) throws BusinessException, InterruptedException, RemotingException, UnsupportedEncodingException, MQClientException, MQBrokerException;
}
