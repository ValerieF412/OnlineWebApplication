package com.hmdp;

import cn.hutool.core.thread.ThreadUtil;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import javax.annotation.Resource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;
import static io.lettuce.core.models.command.CommandDetail.Flag.RANDOM;


@SpringBootTest
@AutoConfigureMockMvc
class HmDianPingApplicationTest {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Resource
    //It allows you to simulate HTTP requests (POST /user/code, etc.) to your controllers without running the actual server.
    private MockMvc mockMvc;
    @Resource
    private IUserService userService;
    @Resource
    //From Jackson. It is used to convert between Java objects and JSON.
    private ObjectMapper mapper;

    private static final Random RANDOM = new Random();

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        for (int i = 0; i < 1000000; i++) {
            values[i % 1000] = "user_" + i;

            if (i % 1000 == 999) {
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }

        // 统计
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("count = " + count);

    }

    @Test
    @SneakyThrows
    @DisplayName("登录1000个用户，并输出到文件中")
    void login() {
        List<String> phoneList = generatePhoneList(1000);
        ExecutorService executorService = ThreadUtil.newExecutor(phoneList.size());
        List<String> tokenList = new CopyOnWriteArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(phoneList.size());
        phoneList.forEach(phone -> {
            executorService.execute(() -> {
                try {
                    // 验证码
                    String codeJson = mockMvc.perform(MockMvcRequestBuilders
                                    .post("/user/code")
                                    .queryParam("phone", phone))
                            .andExpect(MockMvcResultMatchers.status().isOk())
                            .andReturn().getResponse().getContentAsString();

                    Result result = mapper.readerFor(Result.class).readValue(codeJson);
                    Assert.isTrue(result.getSuccess(), String.format("获取“%s”手机号的验证码失败", phone));
                    String code = result.getData().toString();

                    LoginFormDTO formDTO = new LoginFormDTO();
                    formDTO.setCode(code);
                    formDTO.setPhone(phone);
                    String json = mapper.writeValueAsString(formDTO);

                    // token
                    String tokenJson = mockMvc.perform(MockMvcRequestBuilders
                                    .post("/user/login").content(json).contentType(MediaType.APPLICATION_JSON))
                                    .andExpect(MockMvcResultMatchers.status().isOk())
                                    .andReturn().getResponse().getContentAsString();

                    result = mapper.readerFor(Result.class).readValue(tokenJson);
                    Assert.isTrue(result.getSuccess(), String.format("获取“%s”手机号的token失败,json为“%s”", phone, json));
                    String token = result.getData().toString();
                    tokenList.add(token);
                    countDownLatch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });

        countDownLatch.await();
        //If you don’t call shutdown(), the threads will keep running (even if idle),
        //Shutdown() tells Java: no more tasks will be submitted. Finish all current ones, then shut down the thread pool."
        executorService.shutdown();
        writeToTxt(tokenList, "/tokens.txt");
        System.out.println("写入完成！");
    }

    private static void writeToTxt(List<String> list, String suffixPath) throws Exception {

        // 1. 创建文件
        File file = new File(System.getProperty("user.dir") + suffixPath);
        if (!file.exists()) {
            file.createNewFile();
        }

        // 2. 输出
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        for (String content : list) {
            bw.write(content);
            bw.newLine();
        }

        bw.close();
        System.out.println("写入完成！");

    }

    private List<String> generatePhoneList(int size) {
        List<String> phoneList = new CopyOnWriteArrayList<>();
        for (int i = 0; i < size; i++) {
            String phone = generatePhoneNumber();
            phoneList.add(phone);
        }

        return phoneList;
    }

    private static String generatePhoneNumber() {
        String[] validPrefixes = {
                "13", "14", "15", "16", "17", "18", "19"
        };

        String[][] validSecondDigits = {
                {"0","1","2","3","4","5","6","7","8","9"},    // 13
                {"5","7","9"},                                // 14
                {"0","1","2","3","5","6","7","8","9"},        // 15
                {"6"},                                        // 16
                {"0","1","3","5","6","7","8"},                // 17
                {"0","1","2","3","4","5","6","7","8","9"},    // 18
                {"8","9"}                                     // 19
        };

        int index = RANDOM.nextInt(validPrefixes.length);
        String prefix = validPrefixes[index] + validSecondDigits[index][RANDOM.nextInt(validSecondDigits[index].length)];

        StringBuilder phone = new StringBuilder(prefix);
        for (int i = 0; i < 8; i++) {
            phone.append(RANDOM.nextInt(10));
        }

        return phone.toString();
    }

    @Test
    void loadShopData() {
        // 1. 查询商铺数据
        List<Shop> shops = shopService.list();

        // 2. Classify shops by types, shops with the same typeId will belong to one group
        Map<Long, List<Shop>> map = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3. Get the typeId
            Long typeId = entry.getKey();
            // 4. Get the list of shops with the same typeId
            List<Shop> shopList = entry.getValue();
            // 5. Save the list of shops to Redis

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            shopList.forEach(shop -> locations.add(
                    new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(), new Point(shop.getX(), shop.getY()
            ))));

            String key = SHOP_GEO_KEY + typeId;
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testSaveShop2Redis() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY, shop, 1L, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                System.out.println("id = " + redisIdWorker.nextId("order"));
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();

        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        latch.await();
        es.shutdown();
        long end = System.currentTimeMillis();
        System.out.println("Time: " + (end - begin));
    }
}