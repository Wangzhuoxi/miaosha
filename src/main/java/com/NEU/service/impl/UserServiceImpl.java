package com.NEU.service.impl;

import com.NEU.dao.UserDOMapper;
import com.NEU.dao.UserPasswordDOMapper;
import com.NEU.dataobject.UserDO;
import com.NEU.dataobject.UserPasswordDO;
import com.NEU.error.BusinessException;
import com.NEU.error.EmBusinessError;
import com.NEU.service.UserService;
import com.NEU.service.model.UserModel;
import com.NEU.validator.ValidationResult;
import com.NEU.validator.ValidatorImpl;
import net.sf.json.JSONObject;
import org.apache.catalina.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;


/**
 * dataobject:与数据库一一映射，orm。
 *service用于对数据库进行操作，但不能直接返回，必须有model的概念
 *
 */
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserDOMapper userDOMapper;
    @Autowired
    private UserPasswordDOMapper userPasswordDOMapper;
    @Autowired
    private ValidatorImpl validator;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public UserModel getUserById(Integer id) {
        UserDO userDO = userDOMapper.selectByPrimaryKey(id);
        if (userDO == null) {
            return null;
        }
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        return convertFromDataObject(userDO, userPasswordDO);
    }

    //userdo+userpassworddo组装成UserModel对象
    private UserModel convertFromDataObject(UserDO userDO, UserPasswordDO userPasswordDO) {
        if (userDO == null)
            return null;
        UserModel userModel = new UserModel();
        BeanUtils.copyProperties(userDO, userModel);
        if (userPasswordDO != null)
            userModel.setEncrptPassword(userPasswordDO.getEncrptPassword());
        return userModel;
    }

    /**
     * 用户进行注册：将用户传过来的UserModel保存到数据库表中
     * @param userModel
     * @throws BusinessException
     */
    @Override
    @Transactional
    public void register(UserModel userModel) throws BusinessException {
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        //用户传过来的参数是否为空
//        if (StringUtils.isEmpty(userModel.getName())
//                || userModel.getGender() == null
//                || userModel.getAge() == null
//                || StringUtils.isEmpty(userModel.getTelphone())) {
//            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
//        }
        ValidationResult validationResult = validator.validate(userModel);
        if(validationResult.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,validationResult.getErrMsg());
        }

//和用户交互的是userModel,保存的是userDO和password
        UserDO userDO = converFromModel(userModel);
        //实现mode->dataobject方法
            userDOMapper.insertSelective(userDO);
        userModel.setId(userDO.getId());
        UserPasswordDO userPasswordDO = convertPasswordFromModel(userModel);
        userPasswordDOMapper.insertSelective(userPasswordDO);
        return;
    }

    @Override
    public UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException {
        //通过用户的手机获取用户信息
        UserDO userDO=userDOMapper.selectByTelphone(telphone);
        if(userDO==null)
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        UserPasswordDO userPasswordDO=userPasswordDOMapper.selectByUserId(userDO.getId());
        UserModel userModel=convertFromDataObject(userDO,userPasswordDO);

        //对比用户加密的密码是否和传输进来的密码相匹配
        if(!com.alibaba.druid.util.StringUtils.equals(encrptPassword,userModel.getEncrptPassword()))
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);

     return userModel;
    }


    public UserDO converFromModel(UserModel userModel)
    {
        if(userModel==null)
            return null;
        UserDO userDO=new UserDO();
        BeanUtils.copyProperties(userModel,userDO);
        return userDO;
    }
    public UserPasswordDO convertPasswordFromModel(UserModel userModel){
        if(userModel==null)
            return null;
        UserPasswordDO userPasswordDO = new UserPasswordDO();
        userPasswordDO.setEncrptPassword(userModel.getEncrptPassword());
        userPasswordDO.setUserId(userModel.getId());
        return userPasswordDO;
    }

    /**
     * 校验用户信息，如果妹有放入缓存，有的话直接从redis取出
     * @param id
     * @return
     */
    @Override
    public UserModel getUserByIdInCache(Integer id) {

        Object obj = redisTemplate.opsForValue().get("user_validate_" + id);
        JSONObject json = JSONObject.fromObject(obj);
        UserModel    userModel = (UserModel) JSONObject.toBean(json, UserModel.class);
        if(userModel==null)
        {
            userModel=this.getUserById(id);
            redisTemplate.opsForValue().set("user_validate_"+id,userModel);
            redisTemplate.expire("user_validate_"+id,10, TimeUnit.MINUTES);
        }
        return userModel;
    }
}
