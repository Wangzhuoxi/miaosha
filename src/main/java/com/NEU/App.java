package com.NEU;

import com.NEU.dao.UserDOMapper;
import com.NEU.dataobject.UserDO;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hello world!
 *springboot会自动启动内嵌tomcat，加载进去默认的配置。
 restfulcontrol:实现MVC。之前要配置servlet，配置web.xml。现在不用了
 */
@SpringBootApplication(scanBasePackages= {"com.NEU"})
@RestController
@MapperScan("com.NEU.dao")
public class App
{
    @Autowired
    private UserDOMapper userDOMapper;

    @RequestMapping("/")
    public String home(){
        UserDO userDO=userDOMapper.selectByPrimaryKey(1);
        if(userDO==null)
            return "对象用户不存在";
        else
            return userDO.getName();
    }
    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
        SpringApplication.run(App.class,args);
    }

}
