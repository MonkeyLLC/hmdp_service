package com.hmdp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Result queryById(Long id) throws JsonProcessingException {
        //查redis缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (!StringUtils.isEmpty(shopJson)) {
            Shop shop = objectMapper.readValue(shopJson, Shop.class);
            return Result.ok(shop);
        }

        //不存在则查数据库
        Shop shop = this.getById(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        String shopToJson = objectMapper.writeValueAsString(shop);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,shopToJson);
        return Result.ok(shop);
    }
}
