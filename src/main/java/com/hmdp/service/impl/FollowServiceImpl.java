package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        String key = "follows:" + userId;
        if (isFollow) {
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }

        } else {
            boolean removed = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (removed) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        boolean exists = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .exists();
        return Result.ok(exists);
    }

    private Long getCurrentUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return null;
        }
        return user.getId();
    }

    @Override
    public Result followCommons(Long followUserId) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }

        String currentUserKey = "follows:" + userId;
        String followUserKey = "follows:" + followUserId;

        // 1. 获取共同关注的用户id by taking intersection
        Set<String> commonIds = stringRedisTemplate.opsForZSet().intersect(currentUserKey, followUserKey);
        if (commonIds == null || commonIds.isEmpty()) {
            return Result.ok();
        }
        // 2. 解析id
        List<Long> userIds = commonIds.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOs = userService.listByIds(userIds)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOs);
    }
}
