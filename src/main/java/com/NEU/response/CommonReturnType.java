package com.NEU.response;

/**
 * 定义通用的返回对象:返回有意义的信息
 */
public class CommonReturnType {
    //表明对应请求的返回处理结果"success"/"fail"
    //正确;则status=success,返回需要的json数据
    //错误：则status=fail,data使用通用的错误码
    private String status;
    private Object data;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public static  CommonReturnType create(Object result)
    {
        return CommonReturnType.create(result,"success");
    }
    public static CommonReturnType create(Object result,String status)
    {
        CommonReturnType type=new CommonReturnType();
        type.setStatus(status);
        type.setData(result);
        return type;
    }
}
