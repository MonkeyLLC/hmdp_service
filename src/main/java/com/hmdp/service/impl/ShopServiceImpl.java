package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
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

import java.util.concurrent.TimeUnit;

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
        Shop shop;
        //查redis缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopJson)) {
            //如果存在则直接返回
            shop = objectMapper.readValue(shopJson, Shop.class);
            return Result.ok(shop);
        }

        //如果存在，但是返回数据为空，则是为了解决缓存穿透的问题
        if (shopJson != null && shopJson.equals("")) {
            return Result.fail("店铺不存在");
        }

        //访问数据库前，处理缓存击穿问题

        //不存在先检查是否能拿到锁
        try {
            boolean flag = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            //如果拿不到锁
            if (!flag) {
                //如果不能拿到锁则休眠继续查
                Thread.sleep(50);
                return queryById(id);
            }
            //拿到锁了就去查数据库
            shop = this.getById(id);
            if (shop == null) {
                //不存在更新一个空缓存
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }
            //存在则更新缓存
            String shopToJson = objectMapper.writeValueAsString(shop);
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, shopToJson, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unLock(RedisConstants.LOCK_SHOP_KEY + id);
        }

        return Result.ok(shop);
    }

    //获取锁
    public boolean tryLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    //删除锁
    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result updateShopById(Shop shop) {
        //先写数据库再删缓存
        if (shop.getId() == null) {
            Result.fail("店铺ID不能为空");
        }
        this.updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
