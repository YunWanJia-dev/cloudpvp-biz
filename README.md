# server-allocator

`server-allocator` 消费 Matcher 发布的完整比赛对象，为比赛补充服务器信息，再把同一个完整对象回传给 Biz。

## 当前测试链路

- 消费队列：`match.server-allocator.queue`
- 绑定路由键：`match.create`
- 发布路由键：`match.update`
- `match.create` 状态：`WAITING_FOR_SERVER`
- `match.update` 状态：`IN_PROGRESS`
- 当前固定服务器：`{"ip":"127.0.0.1"}`（代码常量，不读取模式或分配配置）

Allocator 当前不区分任何游戏模式，也不做真实云服务器分配逻辑，仅用于打通 MQ 流程。

## 项目结构

```text
cmd/allocator/                 # 进程入口
internal/app/                  # Apollo、RabbitMQ 和用例装配
internal/domain/match/         # 完整 Match 契约
internal/domain/allocation/    # 分配器与发布器端口
internal/usecase/allocation/   # 分配并发布 match.update
internal/infra/allocator/      # 固定测试 IP 实现
internal/infra/config/         # Apollo 配置读取
internal/infra/mq/             # RabbitMQ 连接、拓扑和发布实现
```

## 启动

本地 `config.yaml` 仅用于连接 Apollo。RabbitMQ URL 和交换机名从 Apollo `cloudpvp.mq` namespace 的 `key` 获取，与 Matcher 共用配置；Allocator 不需要其他业务配置。

```bash
go run ./cmd/allocator -config ./config.yaml
```
