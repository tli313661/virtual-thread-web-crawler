# Virtual Thread Web Crawler

一个基于 JDK 25 的高性能轻量级 Web 爬虫示例，用最直接的同步阻塞写法演示虚拟线程在大规模 IO 阻塞场景下的价值。

项目核心问题只有一个：

> 当任务本质上是在“等网络”而不是“烧 CPU”时，JDK 25 之后，我们是否还需要先引入复杂的异步编程模型？

这个项目给出的答案是：

> 很多时候，不需要。直接写同步代码，再把每个任务放进虚拟线程里，就已经足够强。

## 项目目标

- 同时抓取 10,000 个网页
- 核心抓取模式使用 `Executors.newVirtualThreadPerTaskExecutor()`
- HTTP 请求使用 JDK 自带 `HttpClient#send(...)`，保持同步阻塞风格
- 使用 JDK 25 预览特性 `StructuredTaskScope` 管理整组任务的超时、取消和异常传播
- 提供和固定线程池的对比模式，观察 CPU、堆内存和平台线程数差异

## 为什么这个项目值得做

过去做高并发爬虫、网关、批量 API 调用时，很多团队会优先考虑：

- 回调式异步
- `CompletableFuture` 链式编排
- Reactor / WebFlux
- Netty 事件驱动模型

这些方案并不是错的，但它们通常会引入更高的复杂度。对以 IO 阻塞为主的任务来说，虚拟线程给了我们另一种默认选择：

- 代码仍然可以保持同步、线性、好调试
- 并发度可以轻松提升到成千上万
- 线程资源的成本大幅下降
- 不需要先把整个业务改写成异步风格

这正是本项目想直观展示的点。

## 技术栈

- JDK 25
- Maven 3.8+
- `Executors.newVirtualThreadPerTaskExecutor()`
- `java.net.http.HttpClient`
- `StructuredTaskScope`（JDK 25 预览特性）
- `com.sun.net.httpserver.HttpServer` 本地压测服务

## 功能概览

- `crawl`
  对同一个基础 URL 发起大批量抓取请求
- `compare`
  对比虚拟线程与固定线程池在相同任务集上的表现
- `demo-local`
  启动本地 HTTP 服务，离线复现实验，不依赖外网目标站点
- `ResourceSampler`
  采样并输出峰值堆内存、平均进程 CPU 负载、峰值线程数量

## 代码结构

- `src/main/java/io/github/tli/vtcrawler/CrawlerApplication.java`
  命令行入口，解析参数并分发执行模式
- `src/main/java/io/github/tli/vtcrawler/CrawlerEngine.java`
  虚拟线程抓取、固定线程池抓取、批量提交与结果汇总
- `src/main/java/io/github/tli/vtcrawler/ResourceSampler.java`
  运行时资源采样
- `src/main/java/io/github/tli/vtcrawler/LocalTestServer.java`
  本地压测 HTTP 服务
- `src/main/java/io/github/tli/vtcrawler/CrawlReport.java`
  结果统计与终端输出

## 运行要求

- JDK 25
- 支持 `javac` 和 `java`
- Maven 可选

注意：

- `StructuredTaskScope` 在当前 JDK 25 中仍为预览特性
- 编译和运行时都需要开启 `--enable-preview`

## 快速开始

### 1. 编译

使用 Maven：

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
mvn clean compile
```

使用脚本：

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
bash scripts/compile.sh
```

### 2. 直接运行本地 10,000 请求演示

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
bash scripts/run-local-demo.sh
```

默认行为：

- 自动启动一个本地 HTTP 服务
- 自动选择空闲端口
- 生成 10,000 个请求 URL
- 使用虚拟线程执行抓取

### 3. 对比虚拟线程和固定线程池

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
bash scripts/run-local-demo.sh compare
```

## 命令说明

### `crawl`

抓取指定 URL：

```bash
java --enable-preview -cp out io.github.tli.vtcrawler.CrawlerApplication crawl \
  --url https://example.com \
  --count 10000
```

常用参数：

- `--count`
  请求总数，默认 `10000`
- `--timeout-seconds`
  整组任务超时，默认 `120`
- `--request-timeout-millis`
  单请求超时，默认 `15000`
- `--connect-timeout-millis`
  建连超时，默认 `10000`
- `--user-agent`
  请求头中的 User-Agent，默认 `vt-crawler/1.0`

### `compare`

对比虚拟线程和固定线程池：

```bash
java --enable-preview -cp out io.github.tli.vtcrawler.CrawlerApplication compare \
  --url https://example.com \
  --count 10000 \
  --platform-threads 200
```

额外参数：

- `--platform-threads`
  固定线程池大小，默认 `200`

### `demo-local`

启动本地服务并做离线压测：

```bash
java --enable-preview -cp out io.github.tli.vtcrawler.CrawlerApplication demo-local \
  --count 10000 \
  --server-port 0 \
  --server-delay-millis 25 \
  --payload-bytes 1024
```

本地服务参数：

- `--server-port 0`
  自动分配空闲端口，避免端口冲突
- `--server-delay-millis`
  模拟服务端处理延迟
- `--payload-bytes`
  响应体大小
- `--compare`
  在本地服务上连续跑虚拟线程和固定线程池对比

## 示例输出

下面是一组本地验证时的真实输出片段：

```text
== virtual-threads ==
totalRequests      : 10000
completedRequests  : 10000
successCount       : 10000
failureCount       : 0
cancelledCount     : 0
totalBytes         : 10240000
wallClock          : 2357.93 ms
requestsPerSecond  : 4241.01
averageLatency     : 1325.22 ms
p95Latency         : 1977.81 ms
maxLatency         : 2194.20 ms
avgProcessCpuLoad  : 57.92%
peakHeapUsed       : 529 MiB
peakLiveThreads    : 301
peakPlatformThreads: 301
```

说明：

- 这证明项目已经在本机完成过 10,000 请求规模验证
- 该数字是本地回环压测结果，不应直接当作公网吞吐上限
- 真正有参考意义的是程序模型和资源占用趋势

## 对比实验应该怎么看

这个项目并不是为了证明“虚拟线程在任何情况下都更快”，而是为了帮助观察以下事实：

- 虚拟线程通常能以更自然的同步写法承载超大规模阻塞任务
- 在本地回环、延迟极低、业务逻辑很轻的场景下，固定线程池可能跑出更高吞吐
- 但固定线程池往往需要更多平台线程和更高内存占用
- 当任务更接近真实的网络阻塞场景时，虚拟线程的工程收益通常更明显

也就是说，这个项目关注的是：

- 写法是否更简单
- 并发规模是否更容易提升
- 线程资源是否更省
- 超时和取消是否更容易统一管理

而不只是单一的 QPS 数字。

## Structured Concurrency 在这里做了什么

项目中使用 `StructuredTaskScope` 对任务组做了统一治理：

- 为整组任务设置超时边界
- 在作用域结束时回收未完成任务
- 把任务组生命周期限制在可控范围内
- 避免“子任务还在跑，但调用方已经离开”的失控并发问题

它让并发不只是“跑起来”，而是“可以被正确管理”。

## 如何进一步扩展

- 支持多目标 URL 列表输入
- 增加 HTML 解析与内容抽取
- 增加 robots.txt、限流、重试和退避策略
- 增加 CSV / JSON 报表输出
- 接入 JFR 或 Prometheus 做更细粒度的资源观测
- 把固定线程池对比扩展为多组线程数实验

## 一句话结论

对于大量 IO 阻塞型任务，JDK 25 的虚拟线程已经让“同步阻塞写法 + 超高并发”成为一个非常有竞争力的默认方案。

如果你想理解为什么很多 Java 场景在 JDK 25 之后不再急着上复杂异步框架，这个项目就是一个很直接的起点。
