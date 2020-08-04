package com.NEU.error;

public enum EmBusinessError implements CommonError {

    //通用错误类型
    PARAMETER_VALIDATION_ERROR(00001, "参数不合法"),
    UNKNOW_ERROR(10002,"未知错误"),

    //10000开头的为用用户的信息相关的错误定义
    USER_NOT_EXIT(10001,"用户不存在"),
    USER_LOGIN_FAIL(20002,"用户手机号或密码不正确")
    ,
    USER_NOT_LOGIN(20003,"用户还未登陆"),
    STOCK_NOT_ENOUGH(30001,"库存不足"),
    MQ_SEND_FAIL(30002,"库存异步消息失败");

    private int errCode;
    private String errMsg;
private EmBusinessError(int errCode,String errMsg)
{
    this.errCode=errCode;
    this.errMsg=errMsg;
}
    @Override
    public int getErrCode() {
        return this.errCode;
    }

    @Override
    public String getErrorMsg() {
        return this.errMsg;
    }

    @Override
    public CommonError setErrorMsg(String errorMsg) {
        this.errMsg = errorMsg;
        return this;
    }
}
