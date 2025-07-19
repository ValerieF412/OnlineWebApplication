package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Before all the Http requests are processed, this interceptor will be called to check if the user is logged in (if it has an auth token)
 * Each user will be saved in one separate thread by ThreadLocal. When we want to use the user info again, for example:
 * when we handle a delete request in Controller, we can get the user info from the ThreadLocal directly by getUser.getUser().
 */

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDTO userDTO = UserHolder.getUser();

        if (userDTO != null) {
            return true;
        }
        response.setStatus(401);
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }

}
