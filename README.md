# 高性能轻量级 Web 爬虫（JDK 25 / Virtual Threads）

这是一个面向 JDK 25 的轻量级 Web 爬虫示例项目，核心目标是验证一件事：

> 当任务主要是 IO 阻塞时，JDK 25 的虚拟线程可以让我们继续用同步阻塞代码写法，同时把并发规模拉到 10,000 级别，而不再需要先上复杂的异步框架。

项目刻意使用下面这组组合：

- `Executors.newVirtualThreadPerTaskExecutor()` 作为核心并发模型
- JDK 11+ 自带的 `HttpClient#send(...)` 发起同步阻塞请求
- JDK 25 预览特性 `StructuredTaskScope` 统一管理任务组的超时、取消和异常传播

## 项目亮点

- 同时抓取 10,000 个网页
- 不使用固定线程池处理核心爬虫任务
- 提供 `compare` 模式，对比虚拟线程和传统固定线程池在大规模 IO 阻塞场景下的表现
- 提供 `demo-local` 模式，在本地启动一个 HTTP 服务，离线也能完成压测
- 采样输出墙钟时间、成功率、吞吐量、平均 CPU、峰值堆内存、平台线程数等指标

## 环境要求

- JDK 25
- Maven 3.8+

说明：

- `StructuredTaskScope` 在当前 JDK 25 上仍然是预览特性，所以编译和运行需要 `--enable-preview`
- 如果本机首次执行 Maven，可能需要联网下载插件

## 快速开始

### 1. 编译

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
mvn clean compile
```

如果当前环境不方便联网拉 Maven 插件，可以直接用脚本运行：

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
bash scripts/compile.sh
```

### 2. 离线演示 10,000 请求

```bash
bash scripts/run-local-demo.sh
```

默认会：

- 在本机启动一个测试 HTTP 服务
- 生成 10,000 个本地 URL
- 使用虚拟线程进行抓取

### 3. 对比虚拟线程和固定线程池

```bash
bash scripts/run-local-demo.sh compare
```

默认固定线程池大小是 `200`，你可以自行调大或调小：

```bash
java --enable-preview -cp out io.github.tli.vtcrawler.CrawlerApplication compare \
  --url http://127.0.0.1:18080/page \
  --count 10000 \
  --platform-threads 200 \
  --timeout-seconds 120 \
  --request-timeout-millis 15000
```

## 命令说明

### `crawl`

直接抓取指定 URL。

```bash
java --enable-preview -cp out io.github.tli.vtcrawler.CrawlerApplication crawl \
  --url https://example.com \
  --count 10000
```

可选参数：

- `--count 10000`：请求总数
- `--timeout-seconds 120`：整组任务超时
- `--request-timeout-millis 15000`：单个请求超时
- `--connect-timeout-millis 10000`：连接超时
- `--user-agent vt-crawler/1.0`

### `compare`

对同一批 URL 先跑虚拟线程，再跑固定线程池：

```bash
java --enable-preview -cp out io.github.tli.vtcrawler.CrawlerApplication compare \
  --url https://example.com \
  --count 10000 \
  --platform-threads 200
```

### `demo-local`

启动本地 HTTP 服务，并用它做离线压测：

```bash
java --enable-preview -cp out io.github.tli.vtcrawler.CrawlerApplication demo-local \
  --count 10000 \
  --server-port 0 \
  --server-delay-millis 25 \
  --payload-bytes 1024
```

`--server-port 0` 表示自动分配一个空闲端口，适合避免本地端口冲突。

## 观察重点

### 1. 为什么虚拟线程更适合这类任务

抓网页时，大多数时间并不是在算 CPU，而是在等网络返回。传统平台线程在阻塞等待期间会占着系统线程资源，线程数量一大，内存和上下文切换成本就上来了。

虚拟线程把“等待 IO 的任务”变成了更廉价的调度单元。代码仍然可以保持同步阻塞风格，但底层资源占用更低，扩展到 10,000 级别并发也更加自然。

### 2. Structured Concurrency 带来的收益

项目中使用 `StructuredTaskScope` 来统一管理这批抓取任务：

- 可以给整组任务设置统一超时
- 某一组任务结束时，未完成子任务会被有序取消
- 任务生命周期跟随作用域结束，不容易遗留“失控线程”

### 3. 如何观察资源占用

应用运行时会在终端打印：

- 墙钟时间
- 成功 / 失败数
- 吞吐量
- 峰值堆内存
- 平均进程 CPU 负载
- 峰值平台线程数

你也可以在另一个终端里结合 `ps` 观察真实进程占用：

```bash
ps -o pid,rss,vsz,thcount,etime,command -p <PID>
```

## 代码结构

- `CrawlerApplication`：命令行入口
- `CrawlerEngine`：虚拟线程 / 固定线程池 两种抓取实现
- `ResourceSampler`：运行时 CPU / 内存 / 线程采样
- `LocalTestServer`：本地压测 HTTP 服务

## 一个重要结论

这个项目不是在说“线程池已经完全没用了”，而是在说明：

> 对大量 IO 阻塞型任务，JDK 25 的虚拟线程已经让“同步写法 + 超高并发”成为一种非常实用的默认方案。

也正因为如此，很多过去必须依赖复杂异步链路才能解决的问题，现在已经可以先用更直观的阻塞式代码完成，再根据真实瓶颈决定是否需要更重的框架。

补充说明：

- 虚拟线程通常带来更低的平台线程占用和更自然的同步代码结构
- 但它并不保证在所有场景下都绝对更快
- 在“本地回环 + 极低延迟 + 很小业务逻辑”的微基准里，固定线程池可能会因为调度开销更低而显示出更高吞吐
- 一旦任务更接近真实世界的阻塞 IO，虚拟线程在可扩展性、可读性和资源利用率上的优势通常会更明显
