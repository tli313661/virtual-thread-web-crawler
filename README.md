# Virtual Thread Web Crawler

一个基于 JDK 25 的 Web crawler / benchmark 骨架项目。

它现在有两条主线：

- benchmark 模式
  用同步阻塞的 `HttpClient#send(...)` + 虚拟线程，演示高并发 IO 抓取和固定线程池对比
- site crawl 模式
  提供一个更接近真实 crawler 的起点，包含种子 URL、frontier、去重、同域限制、深度限制、HTML 链接提取和页面级报表

这个仓库不再只是“对同一个 URL 打 10000 次请求”的 Loom demo，而是一个可以继续往真实爬虫工程演进的骨架。

## 技术栈

- JDK 25
- Maven 3.8+
- `Executors.newVirtualThreadPerTaskExecutor()`
- `java.net.http.HttpClient`
- `StructuredTaskScope`（benchmark 路径仍保留）
- `com.sun.net.httpserver.HttpServer` 本地 demo 服务

## 现在支持什么

### 1. Benchmark / 对比实验

- `crawl`
  对同一个基础 URL 发起大批量请求，观察虚拟线程下的吞吐和资源占用
- `compare`
  对比虚拟线程和固定线程池
- `demo-local`
  启动本地压测服务，离线复现实验

### 2. Site Crawler Skeleton

- `crawl-site`
  从一个种子页面开始抓取，沿 HTML 链接继续扩散
- `demo-local --site`
  启动本地小站点并运行站点抓取 demo

站点抓取骨架目前包含：

- 种子 URL
- frontier 队列
- URL 去重
- 最大页数限制
- 最大深度限制
- 同域抓取策略
- `robots.txt` 解析与 host 级缓存
- host 最大并发限制
- host 最小请求间隔
- HTML 标题提取
- `<a href>` 链接提取
- 页面级抓取报表
- JSON Lines 输出

## 项目结构

- `src/main/java/io/github/tli/vtcrawler/CrawlerApplication.java`
  命令行入口
- `src/main/java/io/github/tli/vtcrawler/benchmark/CrawlerEngine.java`
  benchmark 模式的批量抓取执行器
- `src/main/java/io/github/tli/vtcrawler/site/SiteCrawlerEngine.java`
  站点级抓取骨架
- `src/main/java/io/github/tli/vtcrawler/site/HtmlLinkExtractor.java`
  简单 HTML 链接与标题提取器
- `src/main/java/io/github/tli/vtcrawler/site/UrlNormalizer.java`
  URL 归一化与过滤
- `src/main/java/io/github/tli/vtcrawler/site/RobotsService.java`
  `robots.txt` 获取、解析和缓存
- `src/main/java/io/github/tli/vtcrawler/site/HostThrottle.java`
  host 级并发 / 节流控制
- `src/main/java/io/github/tli/vtcrawler/site/JsonLinesPageWriter.java`
  JSON Lines 输出
- `src/main/java/io/github/tli/vtcrawler/demo/LocalTestServer.java`
  本地 benchmark 服务和本地站点 demo
- `src/main/java/io/github/tli/vtcrawler/support/ResourceSampler.java`
  资源采样

## 运行要求

- JDK 25
- 支持 `javac` 和 `java`
- Maven 可选

注意：

- `StructuredTaskScope` 仍属于预览特性
- 编译和运行都需要 `--enable-preview`

## 快速开始

### 1. 编译

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
mvn clean compile
```

或：

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
bash scripts/compile.sh
```

### 2. 运行本地 benchmark demo

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
bash scripts/run-local-demo.sh
```

### 3. 运行本地 compare demo

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
bash scripts/run-local-demo.sh compare
```

### 4. 运行本地 site crawl demo

```bash
cd /Users/aurora/Documents/workspace/virtual-thread-web-crawler
bash scripts/run-local-demo.sh site
```

## 命令说明

参数现在同时支持两种写法：

- `--key=value`
- `--key value`

### `crawl`

对同一个基础 URL 发起大量请求：

```bash
java --enable-preview -cp target/classes io.github.tli.vtcrawler.CrawlerApplication crawl \
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
java --enable-preview -cp target/classes io.github.tli.vtcrawler.CrawlerApplication compare \
  --url https://example.com \
  --count 10000 \
  --platform-threads 200
```

### `crawl-site`

从种子 URL 开始抓取站点：

```bash
java --enable-preview -cp target/classes io.github.tli.vtcrawler.CrawlerApplication crawl-site \
  --url https://example.com \
  --max-pages 50 \
  --max-depth 2 \
  --parallelism 50 \
  --max-concurrent-per-host 4 \
  --min-host-delay-millis 100 \
  --jsonl-output ./out/pages.jsonl
```

常用参数：

- `--max-pages`
  最多调度多少个页面，默认 `500`
- `--max-depth`
  最大抓取深度，默认 `2`
- `--parallelism`
  同时运行多少个抓取 worker，默认 `200`
- `--max-concurrent-per-host`
  每个 host 最多允许多少个并发请求，默认 `8`
- `--min-host-delay-millis`
  同一个 host 两次请求启动之间至少间隔多久，默认 `0`
- `--ignore-robots`
  忽略 `robots.txt`，默认会遵守
- `--allow-cross-host`
  允许跨域继续抓取，默认关闭
- `--sample-pages`
  结果里打印多少条样例页面，默认 `10`
- `--jsonl-output`
  把每个页面结果实时写到 JSON Lines 文件
- `--timeout-seconds`
  整个 crawl 的超时，默认 `120`

### `demo-local`

启动本地 demo 服务：

```bash
java --enable-preview -cp target/classes io.github.tli.vtcrawler.CrawlerApplication demo-local \
  --count 10000 \
  --server-delay-millis 25
```

运行本地站点抓取 demo：

```bash
java --enable-preview -cp target/classes io.github.tli.vtcrawler.CrawlerApplication demo-local \
  --site \
  --max-pages 20 \
  --max-depth 2
```

## Site Crawl 示例输出

下面是一组本地站点 demo 的真实输出片段：

```text
== site-crawl ==
seedUrl              : http://127.0.0.1:62922/site
scheduledPages       : 8
visitedPages         : 8
pendingPages         : 0
successCount         : 6
failureCount         : 0
robotsSkippedCount   : 2
htmlPages            : 4
nonHtmlPages         : 2
discoveredLinks      : 20
duplicateLinksSkipped: 5
offHostLinksSkipped  : 3
invalidLinksSkipped  : 0
robotsDeniedPages    : 2
depthLimitedSkipped  : 5
pageBudgetSkipped    : 0
maxObservedDepth     : 2
```

这个输出更像真实 crawler 关心的指标，而不只是 QPS：

- 实际调度了多少页面
- 访问成功 / 失败了多少页面
- 有多少页面被 `robots.txt` 拒绝
- 抓到了多少 HTML 页面和非 HTML 资源
- 链接里有多少重复、跨域、超深度、超预算

如果启用了 `--jsonl-output`，每个页面还会写成一行 JSON，适合后续做：

- 离线分析
- 导入数据仓库
- 接下游抽取 / 清洗管道
- 保留完整抓取明细

## 这个骨架还没有什么

它仍然是 skeleton，不是生产级 crawler。当前还没有：

- 更完整的自适应限流 / 熔断
- 重试和退避
- sitemap 支持
- 持久化 frontier
- 内容抽取规则
- CSV 输出
- 分布式调度

## 下一步适合怎么扩展

- 为 `robots.txt` 增加更完整的规则语义和缓存策略
- 为 host 增加自适应限速和错误熔断
- 把 JSON Lines 再扩展成 CSV / Parquet / Kafka 输出
- 为失败请求增加可配置重试
- 把 HTML 提取器替换成更强的解析器
- 增加 `src/test/java` 自动化测试

## 一句话总结

这个项目现在同时回答了两个问题：

- 虚拟线程能不能把同步阻塞式高并发抓取写得足够简单
- 如果想往真实 crawler 演进，最小可用的工程骨架应该长什么样
