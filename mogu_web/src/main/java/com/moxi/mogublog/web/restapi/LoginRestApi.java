package com.moxi.mogublog.web.restapi;


import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moxi.mogublog.commons.entity.User;
import com.moxi.mogublog.utils.*;
import com.moxi.mogublog.web.global.MessageConf;
import com.moxi.mogublog.web.global.SQLConf;
import com.moxi.mogublog.web.global.SysConf;
import com.moxi.mogublog.xo.service.UserService;
import com.moxi.mogublog.xo.vo.UserVO;
import com.moxi.mougblog.base.enums.EAccountType;
import com.moxi.mougblog.base.enums.EStatus;
import com.moxi.mougblog.base.exception.ThrowableUtils;
import com.moxi.mougblog.base.holder.RequestHolder;
import com.moxi.mougblog.base.validator.group.GetOne;
import com.moxi.mougblog.base.validator.group.Insert;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 用户登录RestApi，系统自带的登录注册功能
 * 第三方登录请移步AuthRestApi
 * </p>
 *
 * @author 陌溪
 * @since 2020年5月6日17:50:23
 */
@RestController
@RequestMapping("/login")
@Api(value = "登录管理RestApi", tags = {"loginRestApi"})
@Slf4j
public class LoginRestApi {


    @Autowired
    private UserService userService;

    @Autowired
    private RedisUtil redisUtil;

    @Value(value = "${BLOG.USER_TOKEN_SURVIVAL_TIME}")
    private Long userTokenSurvivalTime;

    @ApiOperation(value = "用户登录", notes = "用户登录")
    @PostMapping("/login")
    public String login(@Validated({GetOne.class}) @RequestBody UserVO userVO, BindingResult result) {
        ThrowableUtils.checkParamArgument(result);
        String userName = userVO.getUserName();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(SQLConf.STATUS, EStatus.ENABLE);
        queryWrapper.and(wrapper -> wrapper.eq(SQLConf.USER_NAME, userName).or().eq(SQLConf.EMAIL, userName));
        queryWrapper.last("limit 1");
        User user = userService.getOne(queryWrapper);
        if(user == null) {
            return ResultUtil.result(SysConf.ERROR, "用户不存在");
        }

        if(StringUtils.isNotEmpty(user.getPassWord()) && user.getPassWord().equals(MD5Utils.string2MD5(userVO.getPassWord()))) {
            // 更新登录信息
            HttpServletRequest request = RequestHolder.getRequest();
            String ip = IpUtils.getIpAddr(request);
            Map<String, String> userMap = IpUtils.getOsAndBrowserInfo(request);
            user.setBrowser(userMap.get(SysConf.BROWSER));
            user.setOs(userMap.get(SysConf.OS));
            user.setLastLoginIp(ip);
            user.setLastLoginTime(new Date());
            user.updateById();

            // 生成token
            String token = StringUtils.getUUID();
            // 过滤密码
            user.setPassWord("");
            //将从数据库查询的数据缓存到redis中
            redisUtil.setEx(SysConf.USER_TOEKN + SysConf.REDIS_SEGMENTATION + token, JsonUtils.objectToJson(user), userTokenSurvivalTime, TimeUnit.HOURS);
            return ResultUtil.result(SysConf.SUCCESS, token);
        } else {
            return ResultUtil.result(SysConf.ERROR, "账号或密码错误");
        }
    }

    @ApiOperation(value = "用户注册", notes = "用户注册")
    @PostMapping("/register")
    public String register(@Validated({Insert.class}) @RequestBody UserVO userVO, BindingResult result) {
        ThrowableUtils.checkParamArgument(result);
        HttpServletRequest request = RequestHolder.getRequest();
        String ip = IpUtils.getIpAddr(request);
        Map<String, String> map = IpUtils.getOsAndBrowserInfo(request);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.eq(SQLConf.USER_NAME, userVO.getUserName()).or().eq(SQLConf.EMAIL, userVO.getEmail()));
        User user = userService.getOne(queryWrapper);
        if (user != null) {
            return ResultUtil.result(SysConf.ERROR, "用户已存在");
        }
        user = new User();
        user.setUserName(userVO.getUserName());
        user.setNickName(userVO.getNickName());
        user.setPassWord(MD5Utils.string2MD5(userVO.getPassWord()));
        user.setEmail(userVO.getEmail());
        // 设置账号来源，蘑菇博客
        user.setSource("MOGU");
        user.setLastLoginIp(ip);
        user.setBrowser(map.get(SysConf.BROWSER));
        user.setOs(map.get(SysConf.OS));
        user.insert();
        return ResultUtil.result(SysConf.SUCCESS, "注册成功");
    }


    @ApiOperation(value = "退出登录", notes = "退出登录", response = String.class)
    @PostMapping(value = "/logout")
    public String logout(@ApiParam(name = "token", value = "token令牌", required = false) @RequestParam(name = "token", required = false) String token) {
        if(StringUtils.isEmpty(token)) {
            return ResultUtil.result(SysConf.ERROR, MessageConf.OPERATION_FAIL);
        }
        redisUtil.set(SysConf.USER_TOEKN + SysConf.REDIS_SEGMENTATION + token, "");
        return ResultUtil.result(SysConf.SUCCESS, "退出成功");
    }

}
