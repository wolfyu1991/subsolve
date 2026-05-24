# Decrypto 8.5 源码分析

> 反编译自 `decrypto.jar`，CFR 0.152 反编译，42 个 Java 源文件  
> 原始作者：Edwin Olson (MIT), (C) 2006-2008, GPLv2  
> SVN 修订版：r237

---

## 目录

1. [整体架构](#1-整体架构)
2. [核心数据结构](#2-核心数据结构)
3. [求解算法详解](#3-求解算法详解)
4. [评分机制（语言模型）](#4-评分机制语言模型)
5. [GUI 与 CLI 入口](#5-gui-与-cli-入口)
6. [算法流程时序图](#6-算法流程时序图)
7. [关键参数](#7-关键参数)
8. [数据文件格式](#8-数据文件格式)
9. [编译运行说明](#9-编译运行说明)

---

## 1. 整体架构

```
decrypto/
│
├── Solver.java                    # 求解器接口
├── SolverListener.java            # 回调监听器接口
├── Puzzle.java                    # 谜题（密文+线索+字典）
├── Solution.java                  # 解（明文+评分）
├── SolutionSet.java               # 有序解集合（容量限制）
├── Candidate.java                 # 候选词（字节数组+评分）
├── Word.java                      # 单词（模式+候选列表）
├── Dict.java                      # 字典（二分查找+模式缓存）
├── MapSet.java                    # 字母映射位图集合（核心数据结构）
├── Language.java                  # 语言抽象基类
├── EnglishLanguage.java           # 英语（26字母+空格+4-gram）
├── Finisher.java                  # 穷举收尾器
├── StopFlag.java                  # 超时停止标志
├── TreeList.java                  # 可视化平衡二叉树
├── DecryptoCGI.java               # CLI/CGI 入口
├── DecryptoVersion.java           # 版本号
├── MakeDict.java                  # 字典构建工具
├── GABench.java / GATest.java     # 遗传算法测试
│
├── dictattack/                    # === 字典攻击求解器 ===
│   ├── SimpleDictionaryAttack.java  # DFS + 约束传播
│   ├── DictionaryAttackSolver.java  # Solver 包装器
│   ├── Planner.java                 # 规划器接口
│   ├── LazyPlanner.java             # 贪心规划器
│   ├── RandomPlanner.java           # 随机规划器
│   └── DecryptoParameters.java      # 参数配置
│
├── genetic/                      # === 遗传算法求解器 ===
│   ├── GeneticSolver.java         # Solver 包装器+变异
│   ├── GeneticSearch.java         # 爬山搜索
│   ├── GAProblem.java             # 问题定义
│   └── Sampler.java               # 加权随机采样
│
├── statistics/                   # === 统计语言模型 ===
│   ├── BuildStatistics4.java      # 4-gram 训练
│   └── GramFreq.java              # 5-gram 频率
│
├── gui/                          # === Swing GUI ===
│   ├── DecryptoGUI.java           # 主窗口 (840行)
│   ├── DecryptoDictGUI.java       # 字典编辑器
│   ├── HelpFrame.java             # 帮助窗口
│   ├── ExtensionFileFilter.java   # 文件过滤器
│   └── MultiLineCellRenderer.java # 表格多行渲染
│
└── util/                         # === 工具类 ===
    ├── GetOpt.java                # 命令行参数解析
    ├── ParameterGUI.java          # Swing 参数面板
    ├── ParameterListener.java     # 参数变更监听器
    ├── BufferedRandomAccessFile.java  # 缓冲随机访问
    └── ByteArrayWrapper.java      # 字节数组包装器
```

### 包依赖关系

```
gui ──→ dictattack, genetic, util
CGI ──→ dictattack, genetic, util
dictattack ──→ decrypto.* (Puzzle, MapSet, Word, Candidate...)
genetic ──→ decrypto.*, statistics.*
statistics ──→ 独立，仅引 EnglishLanguage
util ──→ 独立工具类
```

---

## 2. 核心数据结构

### 2.1 MapSet —— 字母映射集合

`MapSet` 是**整个系统的核心数据结构**，用位图表示密文字母 → 明文字母的映射可能性。

```
long[] set;     // set[i] 的 bit j = 1 表示 密文字母i 可能映射为 明文字母j
int size;       // 字母表大小（英语=26）
```

**关键操作**：

| 方法 | 作用 |
|---|---|
| `setFullSet()` | 所有字母 → 所有字母都可能 |
| `setMapping(a, b)` | 设置密文 a = 明文 b（清除其他行的 b 位） |
| `forbidMapping(a, b)` | 禁止密文 a → 明文 b |
| `isUniquelyMapped(a)` | 检查密文 a 是否唯一确定（位图仅剩 1 bit） |
| `getMapping(a)` | 获取唯一映射（若已确定），否则返回 -1 |
| `intersectWith(m)` | 与另一个 MapSet 按位与（约束传播） |
| `selfReduce()` | 弧一致性：若某行仅有 2 个候选且另一行有相同 2 候选，消除冲突 |

**性能优化**：`oneHotLog2()` 用 32 项查表法将 `1<<n` 转为 n，O(1) 获取唯一映射。

### 2.2 Word —— 单词表示

```java
class Word {
    byte[] word;         // 密文字节数组
    byte[] pattern;      // 模式（如 "ABC" → [0,1,2], "ABA" → [0,1,0]）
    Candidate[] candidates; // 候选词列表
    int firstcand;       // 当前搜索起始位置（用于剪枝）
    boolean enabled;     // 是否参与求解
}
```

**模式匹配**：`makePattern()` 将单词转为"模式"（首次出现字母编号从 0 开始），相同模式的词共享相同的候选集。

### 2.3 Dict —— 字典文件

二进制字典格式：

```
MAGIC (0xDEC0DEC5 = 233573869)
└── int32
nprops
└── int32
    key (UTF)
    value (UTF)
    ...
--------------------- ↑ 头部 ↑ ---------------------
data records:
    0xFF (分隔符)
    word_length (byte & 0xFF)
    nwords (short)
    word_bytes (word_length × nwords)
    ...
```

查找算法：二分查找定位到模式匹配的位置，然后线性读取同模式的所有词。使用 `WeakHashMap<ByteArrayWrapper, ArrayList<byte[]>>` 做模式缓存。

### 2.4 SolutionSet —— 有序解集合

基于 `TreeList`（自实现平衡二叉树），按评分降序排列，容量固定。仅当评分超过当前最差解时才插入。

### 2.5 TreeList —— 可视化平衡二叉树

自实现的平衡二叉搜索树，同时支持：
- 按索引 O(log n) 访问
- 按值查找/插入/删除
- Swing 可视化渲染（用于调试/演示）

---

## 3. 求解算法详解

### 3.1 字典攻击 (`dictattack`)

#### 总体策略：回溯搜索 + 约束传播

```
solve(recursionDepth, map):
  │
  ├─ 检查停止标志 / 超限 → return
  │
  ├─ 已解所有词 → reportSolution(fullSolution=true)
  │
  ├─ Planner.getAction(solvedWords, map)
  │   ├─ return -1 → 全解
  │   ├─ return -2 → 无解（某词无候选）
  │   ├─ return letter|LETTER_FLAG → 求解单个字母
  │   └─ return wordnum|WORD_FLAG → 求解指定词
  │
  ├─ [LETTER_FLAG] 遍历该字母所有可能的明文映射
  │   ├─ setMappings → reduceCandidates
  │   └─ 递归 solve
  │
  └─ [WORD_FLAG] 遍历该词的所有候选
      ├─ isMappingOkay 检查兼容性
      ├─ setMappings → reduceCandidates 剪枝
      └─ 递归 solve
```

#### Planner 策略

- **LazyPlanner**（默认贪心）：选当前候选最少的词优先搜索，减少分支因子
- **RandomPlanner**（随机搜索阶段）：随机选未解的词

#### 约束传播 (`reduceCandidates`)

```java
for each unsolved word:
    for each candidate:
        if compatible with current map:
            enable these mappings
        else:
            swap to front, increment firstcand (剪枝)
    intersect map with new set
selfReduce()  // 弧一致性
check for conflicts (同一明文被两个密文字母映射)
```

最多迭代 50 次 (`DecryptoParameters.maxReduceIterations`)。

#### 失败恢复策略（`DictionaryAttackSolver`）

当初始搜索未完全解决时：

```
Phase 1: 禁用 1~3 个词重试
  for disableWordCount = 1..3:
    for each combination:
      disable words → re-solve

Phase 2: 随机搜索
  while not timeout:
    RandomPlanner + scrambleWordOrder
    maxIters 递增（5000 → 5250 → 5512...）
    pruneRootNode=false, pruneEachNode=false
```

### 3.2 遗传算法 (`genetic`)

用于**不信任空格**（`trustSpaces=false`）的场景，此时无法准确分词，字典攻击不适用。

#### 初始映射生成 (`makeInitialMap`)

```java
for each cipher letter (按候选数升序排列):
    从可用的明文字母中随机选择一个未使用的
    若冲突则重试整个流程
```

#### 迭代优化 (`iterate` — 爬山法)

```java
generate all swap pairs (i,j):
  shuffle order
  for each swap:
    if improve score:
      keep swap, report if above threshold
  if no improvement → break (局部最优)
```

#### 变异策略 (`SolverThread.makeMutatedSearch`)

三种方式随机选择：

| 概率 | 操作 | 说明 |
|---|---|---|
| 1/3 | 简单变异 | 随机交换 n 个字母映射 |
| 1/3 | 统计引导变异 | 从 5-gram 采样一个 n-gram，推断映射关系后注入 |
| 1/3 | 全新随机 | 重新生成初始映射 |

#### 种群管理

- 保持最多 15 个搜索个体
- 每轮添加新解后，相似度 > 65% 的个体淘汰较差者
- 每个个体迭代 10000 次爬山

### 3.3 Finisher —— 穷举收尾器

当大部分字母已映射但仍有少数未解时，用暴力枚举完成最后映射。

```java
iters = plainletters ^ cipherletters
if iters > 30000:  // 太大则不执行
    return current map
for iter in 0..iters:
    assign remaining mappings
    compute score
    keep best
```

---

## 4. 评分机制（语言模型）

### 4.1 4-gram 语言学模型

**原理**：基于 4-gram 的 log 概率评分。

```
score = Σ log P(plaintext[i] | plaintext[i-3]..plaintext[i-1])
```

- 数据来自 `english4.sta`（含空格）和 `english4ns.sta`（不含空格）
- 维度：28 × 28 × 28 × 28（26字母 + 空格(26) + SUM(27)）
- 每条：float（log 概率）

**训练方式**（`BuildStatistics4`）：
1. 统计 4-gram 计数
2. 回退平滑：计数 < 5 时用 3-gram → 2-gram → 1-gram
3. 加 1 平滑：`P = (count+1) / (total+28)`
4. 取 log：`score = log(P)`
5. 对 SUM 维做期望值计算

### 4.2 5-gram 采样模型

用于遗传算法的统计引导变异。`GramFreq` 从语料构建 5-gram 频率表：
- 只保留累计 60% 的高频 gram
- 按模式索引，支持从模式随机采样
- 数据格式：GZIP 压缩二进制 `.stb` 文件

### 4.3 评分计算路径

```java
EnglishLanguage.computeScore(cipherBytes, map):
  for each cipher byte:
    md = map[cd]        // 通过映射转为明文字母
    if md < 0:          // 未映射
      score += -20.0    // 惩罚
      md = 27 (UNKNOWN)
    score += grams[m0][m1][m2][md]  // 累加 log 概率
```

---

## 5. GUI 与 CLI 入口

### 5.1 CLI 入口：`DecryptoCGI`

```bash
# 基本用法
java -jar decrypto.jar --dictionary english-standard.dat --clues "A=B,M=R" "CIPHERTEXT HERE"

# 完整参数
--dictionary    字典文件          (默认: english-standard.dat)
-c --clues      线索             (默认: "")
--timelimit     超时秒数         (默认: 10.0)
--setsize       最大解数量       (默认: 100)
--puzzlefile    谜题文件         (默认: "")
--scramble      打乱密文并退出   (默认: false)
--trustspaces   信任空格         (默认: true)
```

**决策逻辑**：
- `trustSpaces=true` → `DictionaryAttackSolver`
- `trustSpaces=false` → `GeneticSolver(GAProblem)`

输出格式：JavaScript 调用的 HTML `<script>` 标签。

### 5.2 GUI 入口：`DecryptoGUI`

Swing 主窗口，约 840 行，功能包括：
- 密文输入区（支持从文件加载）
- 线索输入
- 求解控制（开始/停止）
- 结果表格（评分、迭代显示）
- 字典编辑器
- 参数配置面板

---

## 6. 算法流程时序图

```
用户输入密文 + 线索
  │
  ▼
Puzzle 初始化
  ├─ 分词 → 按模式查字典 → 候选列表
  ├─ 应用线索约束 → initialMap
  └─ 计算字母频次直方图
  │
  ▼
┌─ trustSpaces? ──────────┐
│ YES                      │ NO
▼                          ▼
DictionaryAttackSolver    GeneticSolver
│                          │
├─ DFS 搜索                ├─ 随机初始化种群
│  ├─ LazyPlanner          ├─ 爬山迭代优化
│  ├─ reduceCandidates     ├─ 变异注入新知识
│  └─ 剪枝回溯             └─ 淘汰相似个体
│                          │
├─ 失败恢复:               │
│  ├─ 禁词重试             │
│  └─ 随机搜索             │
│                          │
└───────── 合并 ──────────┘
          │
          ▼
    Finisher.bruteForce()
    (若还有未映射字母)
          │
          ▼
    输出解（明文 + 评分）
```

---

## 7. 关键参数

`DecryptoParameters.java`:

| 参数 | 默认值 | 说明 |
|---|---|---|
| `pruneEachNode` | true | 每尝试一个候选后执行约束传播 |
| `pruneRootNode` | true | 根节点也执行剪枝 |
| `betterWordPrioritization` | true | 智能词排序 |
| `enableSingleLetters` | true | 允许单字母求解 |
| `verbosity` | 0 | 日志级别 |
| `maxReduceIterations` | 50 | 约束传播最大迭代次数 |
| `maxRandomWordDisable` | 3 | 失败时最多禁用词数 |
| `maxRandomWordIterations` | 1000 | 随机禁词最大迭代基数 |
| `scrambleAllowIdentityMappings` | false | 打乱时允许恒等映射 |

---

## 8. 数据文件格式

| 文件 | 格式 | 说明 |
|---|---|---|
| `english-standard.dat` | 自定义二进制 | 字典：Magic(4B) + nprops(4B) + props + 词记录 |
| `english4.sta` | GZIP + 二进制 | 4-gram 含空格：dim=4, sz=28, 28^4 个 float |
| `english4ns.sta` | GZIP + 二进制 | 4-gram 不含空格：同上 |
| `gramfreq5.stb` | GZIP + 二进制 | 5-gram 含空格：order + npatterns + 加权采样器数据 |
| `gramfreq5ns.stb` | GZIP + 二进制 | 5-gram 不含空格 |

### 字典文件结构（详细）

```
Offset  Size  Field
0       4     MAGIC (0x0DEC0DEC = 233573869)
4       4     nprops (属性数量)
8       ?     props: key(UTF) + value(UTF) × nprops
?       ?     DATA:
                 255 (0xFF)  ─ 记录分隔符
                 wordlen     ─ 词长 (1 byte)
                 nwords      ─ 同模式词数量 (2 byte)
                 worddata    ─ wordlen × nwords bytes
                 ...重复...
```

---

## 9. 编译与运行

### 环境要求
- Java 1.7+（原始构建使用 JDK 1.7.0-b21, Ant 1.7.0）
- 内存：256MB+

### 运行

```bash
# GUI
java -jar decrypto.jar

# CLI
java -jar decrypto.jar --dictionary english-standard.dat --clues "A=B" "CIPHER TEXT"

# 从文件读取谜题
java -jar decrypto.jar --puzzlefile puzzle.txt
```

### 重建（需 Ant）

```bash
# 编译
javac -d build/classes decrypto_src/src/decrypto/**/*.java decrypto_src/src/decrypto/*.java

# 打包
cd build/classes
jar cfm decrypto.jar META-INF/MANIFEST.MF decrypto/ about.html
```

---

## 附录：核心类关系图

```
Solver (interface)
  ├── DictionaryAttackSolver ──→ SimpleDictionaryAttack ──→ Planner (interface)
  │                                                           ├── LazyPlanner
  │                                                           └── RandomPlanner
  └── GeneticSolver ──→ GeneticSearch ──→ GAProblem
                       ──→ Sampler<T>

SolverListener (interface) ←── DecryptoCGI.MyDecryptoListener
                            └── DecryptoGUI (anonymous)

Puzzle ──→ Dict ──→ BufferedRandomAccessFile
        ──→ Language ──→ EnglishLanguage ──→ BuildStatistics4 (4-gram)
        ──→ Word ──→ Candidate
        ──→ MapSet

MapSet ──→ (位图操作 + oneHotLog2 查表)
Finisher ──→ MapSet + Language.computeScore

SolutionSet ──→ TreeList ──→ (平衡二叉树 + Swing 可视化)
```

---

*分析生成日期：2026-05-22*  
*反编译工具：CFR 0.152*
