package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY;
        Long typeListSize = stringRedisTemplate.opsForList().size(key);

        // Data exists in cache:
        if (typeListSize != null && typeListSize > 0) {
            List<String> typeJsonList = stringRedisTemplate.opsForList().range(key, 0,  typeListSize - 1);
            List<ShopType> list = new ArrayList<>();
            for (String typeJson : typeJsonList) {
                list.add(JSONUtil.toBean(typeJson, ShopType.class));
            }
            return Result.ok(list);
        }

        // Data does not exist in cache:
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList.isEmpty()) {
            return Result.fail("商铺类型不存在");
        }

        List<String> cachedList = new ArrayList<>();
        for (ShopType type : typeList) {
            cachedList.add(JSONUtil.toJsonStr(type));
        }
        stringRedisTemplate.opsForList().rightPushAll(key, cachedList);
        return Result.ok(typeList);
    }
}
