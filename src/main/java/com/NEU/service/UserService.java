package com.NEU.service;

import com.NEU.error.BusinessException;
import com.NEU.service.model.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


public interface UserService {
    public UserModel getUserById(Integer id);
    public void register(UserModel userModel) throws BusinessException;
    public UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException;
    public UserModel getUserByIdInCache(Integer id);
}
