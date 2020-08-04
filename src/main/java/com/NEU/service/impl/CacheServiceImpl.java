package com.NEU.service.impl;

import com.NEU.service.CacheService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
@Service
public class CacheServiceImpl implements CacheService {
    //guava的cache对象，
    private Cache<Object, Object> commonCache=null;
    @PostConstruct
    public void init(){
        commonCache= CacheBuilder.newBuilder()
            //设置缓存容器的初始容量为10
            .initialCapacity(10)
                //缓存中最大可以储存100key，之后会按照LRU策略移除缓存项
                .maximumSize(100)
                //设置多少秒过期
                .expireAfterWrite(60, TimeUnit.SECONDS).build();
    }

    @Override
    public void setCommonCache(String key, Object value) {
        commonCache.put(key,value);
    }

    @Override
    public Object getFromCommonCache(String key) {
        return commonCache.getIfPresent(key);
    }
}
