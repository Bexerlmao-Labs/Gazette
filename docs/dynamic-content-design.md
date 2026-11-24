# 墨水屏动态画面内容存储方案

> 状态：草案 v1
> 适用模块：`module/device`
> 关联表结构：`sql/01_scene.sql`、`sql/02_device.sql`

---

## 1. 背景与目标

墨水屏画面不再是一张静态图，而是：

- **静态部分**：用户在编辑器里绘制/上传的原始画面（背景、边框、装饰、固定文案）——用户改画面时才变；
- **动态部分**：时间、天气、室内温度、折线图、二维码等——内容随时间或数据源变化，**但位置和样式基本不变**。

目标：

1. 只保存**一份**用户原始画面，不保存每一帧；
2. 动态内容要能描述"画在哪、画什么、数据从哪来"，并且要能覆盖从简单文本到**折线图**这类复合元素；
3. 描述方式对人和机器都友好：人能直接读懂/手写，设备端（ESP32，C/C++）和 Java 服务端都能低成本解析。

## 2. 方案选型

| 方案 | 做法 | 问题 |
|---|---|---|
| A. 纯坐标指令流（`(x,y) line`） | 每个元素一条坐标指令 | 只有位置，没有样式、数据绑定；折线图这种复合元素无法表达 |
| B. 存每帧渲染结果 | 每次刷新存一张位图 | 正是要废弃的做法：存储膨胀、无语义、没法单独改动态部分 |
| C. JSON 结构化元素 | 每个元素一个 JSON 对象 | 表达力够，但文本冗长，人不好手写；设备端要引入 JSON 库才能逐行处理 |
| D. **行式 DSL + 数据分离（采用）** | 底图存位图；动态部分存一段一行一元素的 DSL 文本；数据以键值对单独注入 | 新增语法需要约定（见下文），其余优点全占 |

**结论：方案 D。** 核心思想是三件事分离：

```
底图（base_image）   —— 画什么背景，用户绘制， rarely 变
DSL（overlay_dsl）   —— 动态元素"骨架"：位置/类型/样式/绑定字段，用户编辑时才变
数据（data set）     —— 变量与序列的实际取值，频繁变，不落盘（由数据源实时提供）
```

DSL 的本质：**把"动态区域"从底图中抠出来，用文本声明这些区域的渲染规则**。渲染时服务端把 DSL + 数据集合成到底图上，产出最终位图。

## 3. DSL 规范（v1）

### 3.1 总体语法

- 一行一个元素，`#` 开头为注释，空行忽略；
- 通用格式：

```
<type> <x>,<y>[,<w>,<h>] [attr=value ...] ["模板/载荷文本"]
```

- 所有坐标以像素为单位，坐标原点为左上角，均相对底图（不支持嵌套坐标系，保持设备端解析简单）；
- 属性值不含空格时可省略引号；`"..."` 内的文本支持 `{变量}` 占位。

### 3.2 元素类型（v1 集合）

| 类型 | 语法示例 | 说明 | 动态？ |
|---|---|---|---|
| `text` | `text 10,20,220,24 font=2 align=left "室内 {indoor_temp}℃"` | 单行/多行文本，`{var}` 占位 | ✅ |
| `datetime` | `datetime 10,0,120,24 format=%H:%M tz=Asia/Shanghai font=3` | 时间/日期，设备端或服务端生成 | ✅ |
| `icon` | `icon 200,10 name={weather.icon} size=32` | 图标，按绑定值在图标集中取 | ✅ |
| `qr` | `qr 180,200,40 "https://bexerlmao.cc"` | 二维码 | ⚠️ 静态/均可 |
| `line` | `line 0,40,240,40 thickness=1` | 线段（装饰/分隔线） | ❌ |
| `polyline` | `polyline 10,50 30,80 50,60 color=1` | 手绘风格静态折线装饰 | ❌ |
| `rect` | `rect 0,0,240,20 fill=1` | 矩形/色块 | ❌ |
| `chart` | `chart 10,60,220,80 type=line series=indoor_temp,outdoor_temp y-min=-10 y-max=40 legend=1 points=24` | 图表（见 3.3） | ✅ |

### 3.3 chart：折线图/柱状图怎么办

图表是最特殊的元素：它不是"一个值"，而是**一个序列**。设计要点：

1. DSL 里只声明**区域、图型、绑定哪些序列、坐标范围、样式开关**——这些是"骨架"，几乎不变；
2. 序列的**实际数据点**来自数据集（见 §4），以数组注入，例如 `indoor_temp: [12.1, 12.3, ...]`；
3. `points=N` 声明图表取最近 N 个点，数据不足时左对齐留白；
4. `y-min/y-max` 省略时由服务端按当次数据自动归一化；
5. 设备端**不解析图表**，图表由服务端栅格化后随最终位图下发（见 §5），这样 ESP32 端不需要任何图表逻辑。

```
# 例子：双序列折线图 + 图例
chart 10,60,220,80 type=line series=indoor_temp,outdoor_temp legend=1 points=24
# 柱状图：过去 7 天步数
chart 10,150,220,70 type=bar series=step_7d y-min=0 legend=0
```

### 3.4 完整示例

```
# 书房天气面板 v1  (240x416)
datetime 8,4,140,24 format=%H:%M font=3
text 8,34,200,16 font=1 "10月27日 星期一"
line 0,56,240,56 thickness=1

icon 8,64,64 name={weather.icon}
text 76,64,160,40 font=2 "{weather.temp}℃"
text 76,108,160,16 font=1 "{weather.desc} 湿度 {weather.humidity}%"

text 8,140,100,16 font=1 "室内温度"
text 108,140,100,16 font=1 "{indoor_temp}℃"
text 8,160,100,16 font=1 "室内湿度"
text 108,160,100,16 font=1 "{indoor_humidity}%"

chart 8,190,224,90 type=line series=indoor_temp,outdoor_temp legend=1 points=24 y-min=0 y-max=40

qr 8,300,56 "https://bexerlmao.cc/p/42"
text 72,312,150,30 font=1 "扫码查看完整天气趋势"
```

## 4. 数据集与变量绑定

- 变量名采用**点分命名空间**：`weather.temp`、`indoor_temp`、`step_7d`，命名空间约定由服务端数据源注册（weather/clock/sensor/mqtt 等）；
- 数据集是每次渲染时的 JSON 键值对：

```json
{
  "weather": { "icon": "sunny", "temp": 23, "desc": "晴", "humidity": 62 },
  "indoor_temp": 26.5,
  "indoor_humidity": 48,
  "indoor_temp_seq": [25.9, 26.1, 26.5],
  "outdoor_temp_seq": [22.0, 22.4, 23.1],
  "step_7d": [3200, 5100, 4800, 7200, 6100, 9800, 4400]
}
```

- 标量用于 `text/icon`；数组用于 `chart`；
- 变量缺失时该元素**跳过绘制**（而不是报错），避免一个数据源故障毁掉整屏；
- 数据集本身**不入库**，由数据源在渲染时实时组装（未来若做"历史回放"再加表）。

### 4.1 数据源：AI 用量（GLM / DeepSeek，融合命名空间）

编辑器侧组件模板可写 `{变量}` 占位；画布/预览为**设计视图**，一律展示虚拟示例内容
（文本替换示例值、折线图/饼图绘制样例数据），不显示变量名本身；
真实数据只在服务端渲染时注入，变量缺失则跳过该元素。

**融合命名空间 `ai.*`**（推荐）：GLM（账单用量）+ DeepSeek（平台用量 + 官方余额）
统一汇总，配套插件组件组「AI 用量」（总览文本 / 构成饼图 / GLM 月度折线 / DS 月度折线）。
**用量数据一律不落库**：服务端渲染时实时向平台拉取，仅 60s 内存缓存防频控。

| 变量 | 说明 | 来源 |
|---|---|---|
| `ai.total_tokens` | AI 累计 token | GLM 账单 + DeepSeek 平台/代理 |
| `ai.balance.total` | AI 账户余额（元） | DeepSeek `GET /user/balance` |
| `ai.usage.pie_seq` | 用量构成 [GLM, DeepSeek]（饼图） | 融合 |
| `glm.usage.total_tokens` / `requests` | 近 7 天累计用量 / 调用次数 | 智谱账单接口 |
| `glm.usage.seq` / `seq_labels` | 近 7 天每日用量 + 日期标签 | 同上 |
| `glm.usage.month_seq` / `month_labels` | 近 12 个月每月用量（空缺月=0）+ 月份标签 | 同上 |
| `deepseek.balance.total` / `.granted` / `.topped_up` / `pie_seq` | 余额 / 构成 | 官方 `GET /user/balance` |
| `deepseek.usage.total_tokens` / `month_seq` / `month_labels` | DeepSeek 累计/月度用量 | 平台登录态实时查询 |

**DeepSeek 用量的现实约束**（已实测验证）：

- 官方仅开放 `GET /user/balance`（余额），`/dashboard/billing/usage`、`/user/usage` 均 404；
- 平台网页 `platform.deepseek.com/usage` 需登录态，且有 WAF/验证码，无法稳定自动化；
- 因此 DeepSeek 用量有三种方式（**按需选择，用什么配什么**）：

| 方式 | 凭据 | 说明 |
|---|---|---|
| 平台 Cookie（推荐） | 浏览器登录态 Cookie | `platform.deepseek.com/usage` 登录后 F12 复制 Cookie 粘贴；AES 加密存储，不碰账号密码，每次渲染实时查询 |
| 账号密码 | 账号+密码 | ⚠️ AES 加密存服务端数据库，自动登录可能被 WAF/验证码拦截；**UI 红字警告，不信任请勿填入** |

另附 **AI 调用代理** `POST /api/ai/{provider}/chat/completions`（OpenAI 兼容，Key 来自数据源设置）：
统一调用入口，响应原样透传，本服务不落库、不记录用量/对话数据。

**图表元素增强**（折线/柱状/饼图通用）：

- `title=`（支持 {变量}，顶部居中，可编辑）、`x-label=` / `y-label=` 轴含义、
  `x-ticks=<变量>` 横轴刻度（指向 `*_labels` 数组，均匀取样标注）、纵轴自动标注 y-max/y-min；
- `points=N` 槽位布局：数据不足 N 点时左对齐、右侧留白；
- 饼图：占比 ≥12% 的扇区标注百分比；数据全为 0/空缺时画外圈 + "无数据"，不再整块消失；
- 月度序列固定近 12 个月档位，未使用月份填 0，`month_labels` 标注月份。

**配套**：

- 凭证存 `t_data_source`（`auth_type`=api_key/platform_cookie/platform_password，敏感字段 AES 加密，
  密钥 `gazette.security.secret`，生产必须更换默认值）；**本表只存凭证，不存任何用量数据**；
- 配置界面「数据源设置」：只显示画布组件实际依赖的数据源与当前认证方式对应的字段；
  组件未配置时预览/画布编辑条出现「去配置数据源」入口；
- 编辑器接口：`GET /api/token-usage/variables`、`GET|POST /api/token-usage/keys`（只保存填写字段）、
  `GET /api/token-usage/usage?provider=`；
- 服务端 Hutool 拉取，60s 缓存 + 失败降级（DeepSeek 用量查询失败自动降级为代理累计，元素不毁屏）；
- 上游地址可用 `gazette.token-usage.*.url` / `gazette.ai-proxy.*.url` 覆盖（平台用量/登录接口
  可在 F12 确认实际路径后配置）。

## 5. 渲染与刷新流程

```
用户保存场景            数据源变化
     │                      │
     ▼                      ▼
t_scene(base+DSL) ──► 服务端渲染器：底图 + DSL + 数据集 → 最终 1bpp 位图
                              │
                              ▼
                   SHA-256 → 更新 t_device.current_hash
                              │
设备轮询 GET /device/{sn}/image?hash=<旧hash>
                              │
              hash 相同 ──► 304 无更新（省流量、保护墨水屏）
              hash 不同 ──► 下发最终位图（设备端不需要懂 DSL）
```

三层变更对应三种刷新粒度：

| 变更 | 触发 | 结果 |
|---|---|---|
| 数据变化（如天气更新） | 数据源推送/定时拉取 | 重渲染 → hash 变 → 设备下次轮询拉新图 |
| DSL/元素变化 | 用户编辑场景 | 重渲染 → 同上 |
| 底图变化 | 用户重画原始画面 | 重渲染 → 同上 |

> 说明：刷新逻辑只发生在服务端；设备端永远只消费"最终位图 + 哈希"一个协议，**设备固件不需要实现 DSL 解析、图表栅格化**。未来若要减轻服务端压力，可以把 DSL 解释器下放到固件端（DSL 文本很短，比下发位图省流量），这是协议向后兼容的演进方向。

## 6. 表结构落点

| 内容 | 存放位置 |
|---|---|
| 用户原始画面（静态底图） | `t_scene.base_image`（BYTEA；量大后迁对象存储 + `base_image_url`） |
| 动态元素描述 | `t_scene.overlay_dsl`（TEXT，一行一元素） |
| 场景与多设备复用 | `t_device.current_scene_id`（一个场景可绑多台设备） |
| 当前显示内容校验 | `t_device.current_hash`（最终渲染位图哈希） |
| 已废弃 | ~~`t_device_image`（每帧图）~~、~~`t_device_push_log`~~ |

## 7. 演进方向

1. **对象存储**：底图迁 OSS/MinIO，`t_scene` 只存 URL；
2. **元素校验器**：保存 DSL 时做语法校验（未知类型、越界坐标、未注册变量给出警告）；
3. **模板市场**：DSL + 底图打包成一个"模板"复制分发；
4. **设备端直渲**：固件实现 DSL 解释器后，下发协议可从"位图"降级为"DSL + 数据"，进一步省带宽；
5. **动画序列**：墨水屏刷新慢，动态部分按"每 N 分钟一帧"的时隙调度，DSL 可扩展 `refresh=5m` 属性控制元素级刷新频率。
