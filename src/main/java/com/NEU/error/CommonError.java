package com.NEU.error;
//包装器业务异常类实现
public interface CommonError {
    public int getErrCode();
    public String getErrorMsg();
    public CommonError setErrorMsg(String errorMsg);
}
