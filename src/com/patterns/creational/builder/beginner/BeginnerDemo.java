package com.patterns.creational.builder.beginner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 建造者入门演示：一个 6 个参数的 HTTP 请求对象。
 *
 * <p>改造前：长参数构造器 + setter，调用方靠数位置传参，还能把对象改成非法状态；
 * 改造后：链式分步构建，build() 时统一校验，构建完成的对象不再变化。
 */
public class BeginnerDemo {

    public static void main(String[] args) {
        before();
        System.out.println();
        after();
    }

    // ==================================================================
    // 改造前
    // ==================================================================

    private static void before() {
        System.out.println("========== 改造前：长参数构造器 + setter ==========");

        // 位置是：url, method, headers, body, timeoutSeconds, retries
        OldHttpRequest request = new OldHttpRequest(
                "https://api.shop.com/orders", "POST", null, "{\"id\":1}", 0, 0);

        System.out.println("  构造出来的请求：" + request);
        System.out.println("  ↑ 第 3、5、6 个参数是什么？必须回头看定义。");

        request.setTimeoutSeconds(-3);
        System.out.println("  有人把 timeout 改成 -3：" + request);
        System.out.println("  ↑ 对象已经非法，但没有任何地方拦截。");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 6 个参数里 3 个传了默认值，读代码时很难看出哪个是 headers、哪个是 body；");
        System.out.println("  2. setter 让对象在「只设置了一半」时就能被使用；");
        System.out.println("  3. 校验逻辑散落在各个 setter 或调用方，漏一个就出脏数据。");
    }

    static class OldHttpRequest {

        private final String url;
        private String method;
        private Map<String, String> headers;
        private String body;
        private int timeoutSeconds;
        private int retries;

        OldHttpRequest(String url, String method, Map<String, String> headers,
                       String body, int timeoutSeconds, int retries) {
            this.url = url;
            this.method = method;
            this.headers = headers;
            this.body = body;
            this.timeoutSeconds = timeoutSeconds;
            this.retries = retries;
        }

        void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public String toString() {
            return "OldHttpRequest{method=" + method + ", timeout=" + timeoutSeconds
                    + ", retries=" + retries + ", body=" + body + "}";
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：链式构建 + build 校验 ==========");

        HttpRequest request = HttpRequest.builder("https://api.shop.com/orders")
                .method("POST")
                .header("Content-Type", "application/json")
                .body("{\"id\":1}")
                .timeoutSeconds(3)
                .retries(2)
                .build();

        System.out.println("  " + request);
        System.out.println("  ↑ 每个参数前面都有名字，顺序不再重要。");

        try {
            HttpRequest.builder("https://api.shop.com/orders")
                    .timeoutSeconds(-3)
                    .build();
        } catch (IllegalArgumentException e) {
            System.out.println("  非法参数在 build() 被拦住：" + e.getMessage());
        }

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 多了一个 Builder 类，字段要重复声明一遍；");
        System.out.println("  2. 只有 2～3 个参数时，普通构造器更短更清楚；");
        System.out.println("  3. 对象一旦构建完就不可变，需要改只能重新 build。");
    }

    /** 构建完成后不可变：所有字段 final，headers 做了防御性拷贝。 */
    static class HttpRequest {

        private final String url;
        private final String method;
        private final Map<String, String> headers;
        private final String body;
        private final int timeoutSeconds;
        private final int retries;

        private HttpRequest(Builder builder) {
            this.url = builder.url;
            this.method = builder.method;
            this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
            this.body = builder.body;
            this.timeoutSeconds = builder.timeoutSeconds;
            this.retries = builder.retries;
        }

        static Builder builder(String url) {
            return new Builder(url);
        }

        @Override
        public String toString() {
            return "HttpRequest{method=" + method + ", timeout=" + timeoutSeconds
                    + ", retries=" + retries + ", headers=" + headers + ", body=" + body + "}";
        }

        static class Builder {

            private final String url;          // 必填：builder() 时就传入
            private String method = "GET";
            private final Map<String, String> headers = new HashMap<>();
            private String body = "";
            private int timeoutSeconds = 5;
            private int retries = 0;

            private Builder(String url) {
                if (url == null || url.isBlank()) {
                    throw new IllegalArgumentException("url 不能为空");
                }
                this.url = url;
            }

            Builder method(String method) {
                this.method = method;
                return this;
            }

            Builder header(String name, String value) {
                this.headers.put(name, value);
                return this;
            }

            Builder body(String body) {
                this.body = body;
                return this;
            }

            Builder timeoutSeconds(int timeoutSeconds) {
                this.timeoutSeconds = timeoutSeconds;
                return this;
            }

            Builder retries(int retries) {
                this.retries = retries;
                return this;
            }

            HttpRequest build() {
                if (timeoutSeconds <= 0) {
                    throw new IllegalArgumentException("timeoutSeconds 必须大于 0，当前 " + timeoutSeconds);
                }
                if (retries < 0) {
                    throw new IllegalArgumentException("retries 不能为负数");
                }
                return new HttpRequest(this);
            }
        }
    }
}
