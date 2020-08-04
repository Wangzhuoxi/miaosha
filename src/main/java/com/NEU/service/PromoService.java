package com.NEU.service;

import com.NEU.service.model.PromoModel;

public interface PromoService {
    //获得在秒杀活动中或者即将开始秒杀的产品
    PromoModel getPromoByItemId(Integer itemId);
    public void publishPromo(Integer promoId);

    //生成秒杀用的令牌
    String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId);

}
