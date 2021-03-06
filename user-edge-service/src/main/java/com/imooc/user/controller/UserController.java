package com.imooc.user.controller;

import com.imooc.thrift.user.UserInfo;
import com.imooc.user.dto.UserDTO;
import com.imooc.user.redis.RedisClient;
import com.imooc.user.response.LoginResponse;
import com.imooc.user.response.Response;
import com.imooc.user.thrift.ServiceProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.thrift.TException;
import org.apache.tomcat.util.buf.HexUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

@Controller
@RequestMapping("/user")
public class UserController {
    @Autowired
    private ServiceProvider serviceProvider;
    @Autowired
    private RedisClient redisClient;



    @RequestMapping(value="/authentication", method = RequestMethod.POST)
    @ResponseBody
    public UserDTO authentication(@RequestHeader("token") String token) {
        return redisClient.get(token);
    }


    @RequestMapping(value = "/getResult",method = RequestMethod.GET)
    @ResponseBody
    public Response getResult(){
        UserDTO userDTO = redisClient.get("3f39c5dzljbat8kp4p794x6iowdty7nz");
        return new Response("1001",userDTO.getMobile());
    }
    @RequestMapping(value = "/sendVerifyCode",method = RequestMethod.POST)
    @ResponseBody
    public Response sendVerifyCode(  @RequestParam(value = "mobile" ,required = false) String mobile,
                                     @RequestParam(value = "email" ,required = false) String email){
        String code = randomCode("0123456789",6);
        boolean result = false;
        if(StringUtils.isNotBlank(mobile)){
            try {
                result = serviceProvider.getMessageService().sendMobileMessage(mobile,"verify code is:"+code);
                redisClient.set(mobile,code);
            } catch (TException e) {
                e.printStackTrace();
            }
        }else if(StringUtils.isNotBlank(email)){
            try {
                result = serviceProvider.getMessageService().sendEmailMessage(email,"verify code is:"+code);
                redisClient.set(email,code);
            } catch (TException e) {
                e.printStackTrace();
                return Response.exception(e);
            }
            if(!result){
                return Response.SEND_VERIFYCODE_FAILED;
            }
        }else {
            return Response.MOBILE_OR_EMAIL;
        }

        return Response.SUCCESS;
    }
    @RequestMapping(value = "/register",method = RequestMethod.POST)
    @ResponseBody
    public Response register(@RequestParam("username") String username,
                             @RequestParam("password") String password,
                             @RequestParam("realName") String realName,
                             @RequestParam(value = "mobile" ,required = false) String mobile,
                             @RequestParam(value = "email" ,required = false) String email,
                             @RequestParam("verifyCode")String verifyCode) {
        if(StringUtils.isBlank(mobile)&&StringUtils.isBlank(email)){
            return Response.MOBILE_OR_EMAIL;
        }
        if(StringUtils.isNotBlank(mobile)){
            String redisCode = redisClient.get(mobile);
            if(!verifyCode.equals(redisCode)){
                return Response.VERIFYCODE_INVALIN;
            }
        }else{
            String redisCode = redisClient.get(email);
            if(!verifyCode.equals(redisCode)){
                return Response.VERIFYCODE_INVALIN;
            }
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(username);
        userInfo.setRealName(realName);
        userInfo.setPassword(md5(password));
        userInfo.setMobile(mobile);
        userInfo.setEmail(email);
        try {
            serviceProvider.getUserService().registerUser(userInfo);
        } catch (TException e) {
            e.printStackTrace();
            return Response.exception(e);
        }
        return Response.SUCCESS;

    }
    @RequestMapping(value="/login", method = RequestMethod.GET)
    public String login() {
        return "login";
    }
    @RequestMapping(value = "/login",method = RequestMethod.POST)
    @ResponseBody
    public Response login(@RequestParam("username") String username,
                          @RequestParam("password") String password){
        UserInfo userInfo = null;
        //1.?????????????????????
        try {
            userInfo = serviceProvider.getUserService().getUserByName(username);
        } catch (TException e) {
            e.printStackTrace();
            return  Response.USERNAME_PASSWORD_INVALIN;
        }
        if(userInfo == null){
            return Response.USERNAME_PASSWORD_INVALIN;
        }
        if(!userInfo.getPassword().equalsIgnoreCase(md5(password))){
            return Response.USERNAME_PASSWORD_INVALIN;
        }
        //2.??????token
        String token = getToken();
        //3.????????????
        redisClient.set(token,toDto(userInfo),3600);
        return new LoginResponse(token);
    }
    private UserDTO toDto(UserInfo userInfo) {
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(userInfo, userDTO);
        return userDTO;
    }

    private String getToken() {
        return randomCode("0123456789abcdefghijklmnopqrstuvwxyz",32);
    }

    private String randomCode(String s, int size) {
        StringBuilder result = new StringBuilder(size);

        Random random = new Random();
        for (int i = 0; i < size; i++) {
            int loc = random.nextInt(s.length());
            result.append(s.charAt(loc));
        }
        return result.toString();
    }

    private String md5(String password) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(password.getBytes());

            return HexUtils.toHexString(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
}
