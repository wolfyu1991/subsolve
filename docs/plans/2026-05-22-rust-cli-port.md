# Decrypto Rust CLI 移植实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 用 Rust 完整重写 Decrypto CLI 功能，保持与 Java 原版功能一致，支持字典攻击和遗传算法两种求解模式。

**架构:** 自底向上分 6 个阶段构建：基础数据结构 → 语言模型读取 → 字典与谜题 → 字典攻击求解器 → 遗传算法求解器 → CLI 集成。每层只依赖下层接口，通过 trait 解耦求解器与语言模型。

**Tech Stack:** Rust, clap (CLI 参数), flate2 (GZIP 解压), rand, thiserror

---

## 项目结构

```
Cargo.toml
build.rs                # Build script (embeds data files at compile time)
src/
├── main.rs              # CLI 入口
├── lib.rs                # 重新导出所有公共 API
├── resources.rs          # 嵌入式数据访问 (include_bytes!)
├── mapset.rs             # MapSet — 位图映射集合
├── word.rs               # Word — 单词模式与候选
├── candidate.rs          # Candidate — 候选词评分
├── language.rs           # Language trait + EnglishLanguage
├── ngram4.rs             # 4-gram 读取 (java-legacy/english4.sta)
├── ngram5.rs             # 5-gram 文件读取 + Sampler (gramfreq5.stb)
├── dict.rs               # Dict — 字典二进制文件读取
├── puzzle.rs             # Puzzle — 谜题（密文+线索+分词）
├── solution.rs           # Solution + SolutionSet
├── solver.rs             # Solver trait + SolverListener trait
├── stopflag.rs           # StopFlag — 超时停止
├── finisher.rs           # Finisher — 穷举收尾
├── params.rs             # DecryptoParameters — 全局配置
├── planner.rs            # Planner trait (LazyPlanner + RandomPlanner 合并)
├── dict_attack.rs        # DictionaryAttackSolver — DFS + 约束传播
├── ga_problem.rs         # GAProblem — 评分表预计算
├── genetic_search.rs     # GeneticSearch — 爬山搜索
└── genetic_solver.rs     # GeneticSolver — 种群管理
tests/
└── cli_tests.rs          # 集成测试 (11 tests)
java-legacy/              # 原始 Java 项目 + 数据文件
├── decrypto.jar
├── english-standard.dat  # 字典
├── english4.sta / english4ns.sta  # 4-gram 语言模型
└── gramfreq5.stb / gramfreq5ns.stb  # 5-gram 采样器
```

二进制数据文件（复用 Java 原版）:
- `english-standard.dat` — 字典
- `english4.sta` / `english4ns.sta` — 4-gram 语言模型
- `gramfreq5.stb` / `gramfreq5ns.stb` — 5-gram 采样器

---

## 阶段一：基础设施与基础数据结构

### Task 1: 项目骨架

**Files:**
- Create: `Cargo.toml`
- Create: `src/main.rs`
- Create: `src/lib.rs`
- Create: `.gitignore`

**Step 1: 创建 Cargo.toml**

```toml
[package]
name = "subsolve"
version = "0.1.0"
edition = "2021"

[dependencies]
clap = { version = "4", features = ["derive"] }
flate2 = "1"
rand = "0.8"
thiserror = "1"

[dev-dependencies]
tempfile = "3"
```

**Step 2: 创建 main.rs骨架**

```rust
fn main() {
    println!("subsolve Rust CLI — placeholder");
}
```

**Step 3: 初始化项目并验证编译**

Run: `cargo build`
Expected: 编译成功

**Step 4: 提交**

```bash
git add .
git commit -m "feat: init rust project skeleton"
```

---

### Task 2: MapSet — 位图映射集合

**Files:**
- Create: `decrypto-rs/src/mapset.rs`
- Test: `decrypto-rs/src/mapset.rs` (内嵌 #[cfg(test)])

**功能:** 用 `Vec<u64>` 位图表示密文字母到明文字母的映射可能性。`set[i]` 的 bit j = 1 表示密文 i 可映射为明文 j。

**需要移植的方法:**
- `new(size)` — 创建指定大小的空映射
- `new_from_map(map: &[i32])` — 从确定的映射数组创建
- `copy_from(&self) -> Self`
- `set_full_set()` — 所有行全 1
- `set_empty_set()` — 所有行全 0
- `forbid_mapping(a, b)` — 禁止密文 a → 明文 b
- `set_mapping(a, b)` — 设置密文 a = 明文 b（清除其他行 b 位）
- `set_mappings(a: &[u8], b: &[u8])` — 批量设置
- `enable_mappings(a, b)` — 批量启用
- `is_uniquely_mapped(a) -> bool`
- `get_mapping(a) -> Option<u8>` — 获取唯一映射
- `is_mapping_ok(a, b) -> bool`
- `is_mappings_ok(a, b) -> bool`
- `intersect_with(other: &MapSet)` — 约束传播
- `self_reduce()` — 弧一致性
- `make_map() -> Vec<i32>` — 导出映射数组
- `has_mappings(c) -> bool`

**关键辅助函数:** `one_hot_log2(v: u64) -> usize` — 查表法 O(1)

**Step 1: 编写测试**

```rust
#[test]
fn test_set_full_set_and_get_mapping() {
    let mut ms = MapSet::new(26);
    ms.set_full_set();
    // 所有行初始全 1
    for i in 0..26u8 {
        assert!(ms.has_mappings(i));
        assert!(!ms.is_uniquely_mapped(i));
    }
}

#[test]
fn test_set_and_get_mapping() {
    let mut ms = MapSet::new(26);
    ms.set_full_set();
    ms.set_mapping(0, 5);  // A → F
    assert!(ms.is_uniquely_mapped(0));
    assert_eq!(ms.get_mapping(0), Some(5));
    // 其他行不应包含 5
    for i in 1..26u8 {
        assert!(!ms.is_mapping_ok(i, 5));
    }
}

#[test]
fn test_forbid_mapping() {
    let mut ms = MapSet::new(26);
    ms.set_full_set();
    ms.forbid_mapping(3, 7);
    assert!(!ms.is_mapping_ok(3, 7));
    assert!(ms.is_mapping_ok(3, 8));
}

#[test]
fn test_intersect_with_propagation() {
    // A→{B,C}, B→{B,C} 的 2 候选场景
    // intersect 后如果 A 确定为 B，B 自动排除 B
}

#[test]
fn test_self_reduce() {
    // 弧一致性测试
}

#[test]
fn test_one_hot_log2() {
    assert_eq!(MapSet::one_hot_log2(1 << 0), 0);
    assert_eq!(MapSet::one_hot_log2(1 << 5), 5);
    assert_eq!(MapSet::one_hot_log2(1 << 25), 25);
}

#[test]
fn test_new_from_map() {
    let map = [0, 2, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25];
    let ms = MapSet::new_from_map(&map);
    for i in 0..26u8 {
        assert!(ms.is_uniquely_mapped(i));
        assert_eq!(ms.get_mapping(i), Some(map[i as usize] as u8));
    }
}
```

**Step 2–4:** 实现完整 `MapSet`，运行 `cargo test mapset` 通过

**Step 5: 提交**

```bash
git add decrypto-rs/src/mapset.rs
git commit -m "feat: add MapSet bitmap mapping structure"
```

---

### Task 3: Word + Pattern — 单词与模式

**Files:**
- Create: `decrypto-rs/src/word.rs`
- Test: 内嵌 `#[cfg(test)]`

**功能:** 表示密文中的一个单词，包含模式生成和数组比较。

**关键函数:**
- `make_pattern(word: &[u8]) -> Vec<u8>` — 生成模式（首次出现字母编号从 0 递增）
- `compare_arrays(a: &[u8], b: &[u8]) -> Ordering` — 先比长度，再逐字节比

**Step 1: 测试**

```rust
#[test]
fn test_make_pattern() {
    // "ABA" → [0, 1, 0]
    assert_eq!(Word::make_pattern(&[0, 1, 0]), vec![0, 1, 0]);
    // "ABC" → [0, 1, 2]
    assert_eq!(Word::make_pattern(&[0, 1, 2]), vec![0, 1, 2]);
    // "AABBC" → [0, 0, 1, 1, 2]
    assert_eq!(Word::make_pattern(&[0, 0, 1, 1, 2]), vec![0, 0, 1, 1, 2]);
}

#[test]
fn test_compare_arrays() {
    assert_eq!(Word::compare_arrays(&[0,1], &[0,1]), Ordering::Equal);
    assert_eq!(Word::compare_arrays(&[0,1], &[0,2]), Ordering::Less);
    assert_eq!(Word::compare_arrays(&[0,1,2], &[0,1]), Ordering::Greater);
}
```

**Step 2–4:** 实现并测试通过

**Step 5: 提交**

```bash
git add decrypto-rs/src/word.rs
git commit -m "feat: add Word pattern generation"
```

---

### Task 4: Candidate — 候选词

**Files:**
- Create: `decrypto-rs/src/candidate.rs`
- Test: 内嵌

**功能:** 候选词 = 字节数组 + 评分，按评分降序排列。

```rust
pub struct Candidate {
    pub cand: Vec<u8>,
    pub score: f64,
}
```

**Step 1: 测试**

```rust
#[test]
fn test_candidate_sorting() {
    let mut cands = vec![
        Candidate { cand: vec![0,1], score: -2.5 },
        Candidate { cand: vec![2,3], score: -1.0 },
        Candidate { cand: vec![4,5], score: -3.0 },
    ];
    cands.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap());
    assert_eq!(cands[0].score, -1.0);
    assert_eq!(cands[1].score, -2.5);
    assert_eq!(cands[2].score, -3.0);
}

#[test]
fn test_to_candidates() {
    // 调用 Candidate::to_candidates 从 byte 列表和评分函数生成排序后的候选列表
}
```

**Step 2–5:** 实现 → 测试 → 提交

---

### Task 5: Language + EnglishLanguage — 语言基础

**Files:**
- Create: `decrypto-rs/src/language.rs`
- Test: 内嵌

**功能:** 语言 trait + 英语实现（字符↔字节转换、密文分词、4-gram 评分、5-gram 评分）。

**Trait 定义:**

```rust
pub trait Language {
    fn letter_count(&self) -> usize;
    fn char_to_byte(&self, c: char) -> u8;
    fn byte_to_char(&self, b: u8) -> char;
    fn string_to_bytes(&self, s: &str) -> Vec<u8>;
    fn bytes_to_string(&self, bytes: &[u8]) -> String;
    fn cipher_text_to_words(&self, ciphertext: &str) -> Vec<Word>;
    fn translate_string(&self, ciphertext: &str, map: &MapSet, spaces: Option<&[i32]>) -> String;
    fn compute_score_4gram(&self, cipher_bytes: &[u8], map: &MapSet, ngrams: &Ngram4) -> f64;
    fn compute_candidate_score(&self, candidate: &[u8]) -> f64;
}
```

**EnglishLanguage 特性:**
- `letter_count()` → 26
- `char_to_byte()`: 空格→26, A-Z→0-25, 其他→26
- `byte_to_char()`: 0-25→A-Z, 26→空格, -1→~
- `compute_candidate_score()`: 使用 4-gram（含空格）计算候选词的对数概率
- `compute_score_4gram()`: 使用当前映射解码密文后 4-gram 评分

**Step 1: 测试**

```rust
#[test]
fn test_char_to_byte() {
    let lang = EnglishLanguage::new();
    assert_eq!(lang.char_to_byte('A'), 0);
    assert_eq!(lang.char_to_byte('Z'), 25);
    assert_eq!(lang.char_to_byte(' '), 26);
    assert_eq!(lang.char_to_byte('~'), 26);
}

#[test]
fn test_byte_to_char() {
    let lang = EnglishLanguage::new();
    assert_eq!(lang.byte_to_char(0), 'A');
    assert_eq!(lang.byte_to_char(25), 'Z');
    assert_eq!(lang.byte_to_char(26), ' ');
}

#[test]
fn test_string_to_bytes() {
    let lang = EnglishLanguage::new();
    assert_eq!(lang.string_to_bytes("ABC"), vec![0, 1, 2]);
    assert_eq!(lang.string_to_bytes("hello"), vec![7, 4, 11, 11, 14]);
}

#[test]
fn test_cipher_text_to_words() {
    let lang = EnglishLanguage::new();
    let words = lang.cipher_text_to_words("HELLO WORLD");
    assert_eq!(words.len(), 2);
}
```

**Step 2–5:** 实现 → 测试 → 提交

---

### Task 6: Ngram4 — 4-gram 二进制读取

**Files:**
- Create: `decrypto-rs/src/ngram4.rs`
- Test: 内嵌

**功能:** 读取 `english4.sta` / `english4ns.sta` 文件，返回 `[[[[f32; 28]; 28]; 28]; 28]` 数组。

**文件格式 (GZIP):**
```
[i32: 4 = dim] [i32: 28 = sz] [f32 × 28^4] (小端)
```

**Step 1: 测试**

```rust
#[test]
fn test_read_english4_sta() {
    let ngrams = Ngram4::from_file("java-legacy/english4.sta").unwrap();
    assert_eq!(ngrams.dim(), 28);
    // 第一个值应该是 log 概率（负数）
    assert!(ngrams.get(0, 0, 0, 0) < 0.0);
    // 读取约 100 个值，验证数据完整
    let mut count = 0;
    for i in 0..28 {
        for j in 0..28 {
            for k in 0..28 {
                for w in 0..28 {
                    count += 1;
                    if count > 100 { break; }
                    assert!(ngrams.get(i,j,k,w).is_finite());
                }
                if count > 100 { break; }
            }
            if count > 100 { break; }
        }
        if count > 100 { break; }
    }
}

#[test]
fn test_read_english4ns_sta() {
    let ngrams = Ngram4::from_file("java-legacy/english4ns.sta").unwrap();
    assert_eq!(ngrams.dim(), 28);
}
```

**Step 2–5:** 实现 → `cargo test` 通过（需要数据文件在工作目录）→ 提交

---

### Task 7: Ngram5 + Sampler — 5-gram 采样

**Files:**
- Create: `decrypto-rs/src/ngram5.rs`
- Test: 内嵌

**功能:** 读取 `gramfreq5.stb` / `gramfreq5ns.stb`，返回从模式到采样器的 HashMap。

**Sampler:**

```rust
pub struct Sampler<T> {
    proportions: Vec<f64>,
    objs: Vec<T>,
    total_proportion: f64,
}

impl<T> Sampler<T> {
    pub fn new(proportions: Vec<f64>, objs: Vec<T>) -> Self;
    pub fn sample(&self, v: f64) -> &T;
}
```

**文件格式 (GZIP):**
```
[i32: MAGIC = -1438373319] [i32: order] [i32: npatterns]
  各 pattern:
    [i32: ngrams]
      各 gram:
        [byte × order] (字符数据)
        [f64: count]
```

**Step 1: 测试**

```rust
#[test]
fn test_read_gramfreq5_stb() {
    let map = Ngram5::from_file("java-legacy/gramfreq5.stb").unwrap();
    assert!(map.len() > 0);
    // 验证每个 pattern 的采样器
    for (pattern, sampler) in &map {
        assert!(pattern.len() <= 5);
        let sample = sampler.sample(0.5);
        assert_eq!(sample.len(), 5);
    }
}

#[test]
fn test_sampler() {
    let s = Sampler::new(vec![1.0, 2.0, 3.0], vec!["a", "b", "c"]);
    assert_eq!(*s.sample(0.0), "a");   // 0.0 落在 [0, 1)
    assert_eq!(*s.sample(0.16), "b");  // 1.0/6.0 ≈ 0.166, 在 [1/6, 3/6)
    assert_eq!(*s.sample(0.5), "b");   // 3.0/6.0 = 0.5, 在 [3/6, 6/6)
    assert_eq!(*s.sample(0.9), "c");
}
```

**Step 2–5:** 实现 → 测试 → 提交

---

### Task 8: Dict — 字典二进制读取

**Files:**
- Create: `decrypto-rs/src/dict.rs`
- Test: 内嵌

**功能:** 读取 `english-standard.dat`，通过模式匹配查找候选词。使用二分查找定位，`HashMap` 缓存。

**文件格式:**
```
[i32: MAGIC = 233573869 (0x0DEC0DED)]
[i32: nprops]
  [UTF String: key] [UTF String: value] × nprops
数据记录:
  [byte: 0xFF]
  [byte: wordlen (无符号)]
  [i16: nwords (无符号, 大端)]
  [byte × wordlen × nwords]  // 所有同模式词
  重复...
```

**Step 1: 测试**

```rust
#[test]
fn test_dict_open() {
    let dict = Dict::from_file("java-legacy/english-standard.dat").unwrap();
    assert_eq!(dict.get_property("langclass"), Some("decrypto.EnglishLanguage"));
}

#[test]
fn test_dict_lookup_by_pattern() {
    let dict = Dict::from_file("java-legacy/english-standard.dat").unwrap();
    let lang = EnglishLanguage::new();
    let pattern = Word::make_pattern(&lang.string_to_bytes("HELLO"));
    let results = dict.lookup(&pattern).unwrap();
    assert!(results.len() > 0);
    // "HELLO" 应该在字典中
    let hello_bytes = lang.string_to_bytes("HELLO");
    assert!(results.contains(&hello_bytes));
}

#[test]
fn test_dict_lookup_nonexistent() {
    let dict = Dict::from_file("java-legacy/english-standard.dat").unwrap();
    let pattern = Word::make_pattern(&[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
    let results = dict.lookup(&pattern).unwrap();
    assert!(results.is_empty());
}

#[test]
fn test_dict_caching() {
    let dict = Dict::from_file("java-legacy/english-standard.dat").unwrap();
    let pattern = Word::make_pattern(&[0, 1, 2]);
    // 两次查询应返回相同结果
    let r1 = dict.lookup(&pattern).unwrap();
    let r2 = dict.lookup(&pattern).unwrap();
    assert_eq!(r1.len(), r2.len());
}
```

**Step 2–5:** 实现（二分查找 + 缓存）→ 测试 → 提交

---

### Task 9: Solution + SolutionSet — 解容器

**Files:**
- Create: `decrypto-rs/src/solution.rs`
- Test: 内嵌

```rust
pub struct Solution {
    pub solution: String,
    pub score: f64,
}

pub struct SolutionSet {
    set: Vec<Solution>,
    capacity: usize,
    worst: f64,
    best: f64,
}
```

- `SolutionSet::new(capacity)` — 容量限制的有序集合
- `add(sol) -> bool` — 按评分降序插入，超标则丢弃
- `would_add(score) -> bool`
- `get_element(idx) -> &Solution`

**Step 1–5:** 实现 → 测试排序和容量限制 → 提交

---

### Task 10: StopFlag — 超时控制

**Files:**
- Create: `decrypto-rs/src/stopflag.rs`
- Test: 通过集成测试验证

**功能:** 线程安全的超时标志。启动后台线程在 `max_time` 后标记停止。

```rust
pub struct StopFlag {
    stop: Arc<AtomicBool>,
    start_time: Instant,
    max_time: Duration,
}
```

**Step 1–5:** 实现 → 测试 → 提交（Rust 使用 `std::sync::atomic` + `Arc`）

---

## 阶段二：求解器基础设施

### Task 11: Solver + SolverListener traits

**Files:**
- Create: `decrypto-rs/src/solver.rs`

```rust
pub trait SolverListener {
    fn on_message(&mut self, msg: &str);
    fn on_progress(&mut self, progress: f64);
    fn on_finished(&mut self, elapsed: f64);
    fn on_solution(&mut self, mapset: &MapSet, spaces: Option<&[i32]>, full_solution: bool, score: f64);
}

pub trait Solver {
    fn add_listener(&mut self, listener: Box<dyn SolverListener>);
    fn start(&mut self, max_time: f64);
    fn stop(&mut self);
    fn elapsed_time(&self) -> f64;
}
```

**Step 1–3:** 仅定义 trait，无实现。编译通过即可。

---

### Task 12: Params + Planner traits

**Files:**
- Create: `decrypto-rs/src/params.rs` — 所有 `DecryptoParameters` 静态配置
- Create: `decrypto-rs/src/planner.rs` — Planner trait
- Create: `decrypto-rs/src/lazy_planner.rs` — LazyPlanner
- Create: `decrypto-rs/src/random_planner.rs` — RandomPlanner

**params.rs:**
```rust
pub struct DecryptoParameters {
    pub prune_each_node: bool,
    pub prune_root_node: bool,
    pub enable_single_letters: bool,
    pub max_reduce_iterations: usize,
    pub max_random_word_disable: usize,
    pub max_random_word_iterations: usize,
    // ... 所有默认值同 Java
}
```

**Planner trait:**
```rust
pub trait Planner {
    fn get_action(&self, solved_words: &[bool], map: &MapSet, puzzle: &Puzzle) -> i32;
}
// 返回值：-1=全解, -2=无解, letter|0x200000=单字母, wordnum|0x100000=词
```

**LazyPlanner:** 选择候选最少的未解词

**RandomPlanner:** 随机选未解词

**Step 1–5:** 实现并单元测试 → 提交

---

### Task 13: Puzzle — 谜题类

**Files:**
- Create: `decrypto-rs/src/puzzle.rs`
- Modify: `decrypto-rs/src/word.rs` (添加 `Word` struct)

**功能:** 解析密文 → 分词 → 按模式查字典 → 候选列表 → 直方图

```rust
pub struct Puzzle {
    pub raw_cipher_text: String,
    pub cipher_text: String,
    pub cipher_bytes: Vec<u8>,
    pub trust_spaces: bool,
    pub clues: String,
    pub initial_map: MapSet,
    pub histogram: Vec<i32>,
    pub historder: Vec<u8>,
    pub words: Vec<Word>,
    pub lang: EnglishLanguage,
    pub num_ciphertext_letters: usize,
    pub num_unique_ciphertext_letters: usize,
}

pub struct Word {
    pub text: String,
    pub word: Vec<u8>,
    pub pattern: Vec<u8>,
    pub num_unique_letters: usize,
    pub candidates: Vec<Candidate>,
    pub enabled: bool,
    pub firstcand: usize,
}
```

**方法:**
- `Puzzle::new(dict, ctext, clues, trust_spaces, allow_identity)`
- `handle_clues(clues)` — 解析 `A=B`, `A!=B` 等线索格式
- `despace_string(s) -> String` — 智能去空格
- `only_alpha_characters(s) -> String`
- `only_alpha_space_characters(s) -> String`

**Step 1: 测试**

```rust
#[test]
fn test_handle_clues_simple() {
    let dict = Dict::from_file("java-legacy/english-standard.dat").unwrap();
    let puzzle = Puzzle::new(&dict, "HELLO WORLD", "H=E", true, true);
    assert!(puzzle.initial_map.is_mapping_ok(7, 4));  // H→E
    assert!(!puzzle.initial_map.is_mapping_ok(7, 7)); // H!→H (被排除了)
}

#[test]
fn test_despace_string() {
    // 空格占 >40% 时压缩连续空格
    assert_eq!(Puzzle::despace_string("A  B"), "A B");
    assert_eq!(Puzzle::despace_string("A B"), "A B");
}

#[test]
fn test_clue_inequality() {
    let dict = Dict::from_file("java-legacy/english-standard.dat").unwrap();
    let puzzle = Puzzle::new(&dict, "TEST", "A!=BCD", true, true);
    assert!(!puzzle.initial_map.is_mapping_ok(0, 1)); // A!→B
    assert!(!puzzle.initial_map.is_mapping_ok(0, 2)); // A!→C
    assert!(!puzzle.initial_map.is_mapping_ok(0, 3)); // A!→D
}
```

**Step 2–5:** 实现密文分词、去重、候选查询 → 测试 → 提交

---

### Task 14: Finisher — 暴力收尾

**Files:**
- Create: `decrypto-rs/src/finisher.rs`

**功能:** 当大部分映射唯一确定了，但仍有少量未映射字母时，遍历剩余可能性找出最优解。

```
Finisher::new(puzzle, map) → 提取未映射的密文字母和可用的明文字母
Finisher::brute_force() → 暴力枚举所有排列组合（>30000 种则跳过）
```

**Step 1–5:** 实现 → 测试（用已知 puzzle 验证）→ 提交

---

## 阶段三：字典攻击求解器

### Task 15: SimpleDictionaryAttack — DFS+约束传播

**Files:**
- Create: `decrypto-rs/src/simple_dict_attack.rs`

**功能:** 核心 DFS 搜索：递归选择未解的词 → 遍历候选 → 约束传播 → 回溯。

**关键方法:**
- `solve(stopflag)` — 入口：复制 initial_map，调用递归
- `solve_inner(recursion_depth, map)` — 递归核心
  - 调用 planner.get_action() 选择下一个词或字母
  - WORD_FLAG：遍历候选，set_mappings → reduce_candidates → 递归
  - LETTER_FLAG：遍历该密文字母的所有可能明文 → 递归
- `reduce_candidates(map) -> bool` — 约束传播（是否不一致）
  - 对每个未解词，检查候选与当前映射的兼容性
  - 不兼容的候选移到 `firstcand` 之前
  - `intersect_with` + `self_reduce` 传播约束
- `report_solution(map, full)` — 回调

**Step 1: 测试（小谜题）**

```rust
#[test]
fn test_simple_dict_attack_basic() {
    // 最简单的 case：每个词只有 1 个候选
    // 假设输入 "HELLO WORLD"，但用简单映射
    let dict = Dict::from_file("java-legacy/english-standard.dat").unwrap();
    // 用已知答案验证求解结果
}
```

**Step 2–5:** 实现完整 DFS → 测试验证全解 → 提交

---

### Task 16: DictionaryAttackSolver — 完整求解器

**Files:**
- Create: `decrypto-rs/src/dict_solver.rs`

**功能:** 实现 Solver trait，封装完整的字典攻击求解策略：

1. **初始搜索**: LazyPlanner + SimpleDictionaryAttack
2. **失败恢复 Phase 1**: 依次禁用 1~3 个词重试
3. **失败恢复 Phase 2**: RandomPlanner + scrambleWordOrder 随机搜索

**Step 1: 集成测试**

```rust
#[test]
fn test_dict_solver_with_real_puzzle() {
    // 使用本地数据文件运行完整的字典攻击求解
    let puzzle_text = "PG XOYHLM XOYLY PZ GH TPUUYLYGRY EYXBYYG XOYHLM WGT JLWRXPRY";
    let clues = "X=T M=Y";
    // 预期能找到完整解
}
```

**Step 2–5:** 实现 Solver trait → 集成测试 → 提交

---

## 阶段四：遗传算法求解器

### Task 17: GAProblem — 评分表预计算

**Files:**
- Create: `decrypto-rs/src/ga_problem.rs`

**功能:** 预计算密文中 4-gram 的索引表，加速评分计算。使用`twolettertable` 快速定位只涉及少量密文字母的四元组。

```rust
pub struct GAProblem {
    pub puzzle: Puzzle,
    pub tetragrams: Ngram4,
    pub tetragrams_ns: Ngram4,
    twolettertable: Vec<Vec<Vec<i16>>>,  // [26][26][]
}
```

**方法:**
- `precompute_indices_table()` — 统计密文中 4-gram 的规范索引
- `compute_score(map: &[i32]) -> f64` — 快速评分

**Step 1–5:** 实现 → 测试评分与 EnglishLanguage 一致 → 提交

---

### Task 18: GeneticSearch — 爬山搜索

**Files:**
- Create: `decrypto-rs/src/ga_search.rs`

**功能:** 单次爬山搜索，从随机初始映射开始，迭代交换字母对直到收敛。

```rust
pub struct GeneticSearch {
    pub problem: GAProblem,
    pub map: Option<Vec<i32>>,
    pub score: f64,
    pub total_iterations: usize,
}
```

**方法:**
- `new(problem, stopflag)` — 生成随机初始映射
- `make_initial_map() -> bool` — 贪心+随机初始化
- `iterate(max_iterations, protect: Option<&[bool]>)` — 爬山迭代
  - 生成所有 `(i, j)` 交换对
  - 打乱后尝试每个交换
  - 保留提升评分的交换
  - 直到无改进或到达迭代上限
- `mutate(npermutes) -> Option<GeneticSearch>` — 随机排列 n 个映射
- `find_spaces4(cipherbytes, map) -> Vec<i32>` — 在不信任空格时猜测空格位置（4-gram）

**Step 1–5:** 实现 → 简单测试 → 提交

---

### Task 19: GeneticSolver — 种群管理

**Files:**
- Create: `decrypto-rs/src/ga_solver.rs`

**功能:** 实现 Solver trait，管理 15 个搜索个体的种群：
- 新建随机个体 / 变异最佳个体 / 5-gram 统计引导变异
- 相似度 > 65% 淘汰较差者
- 保持种群 ≤ 15

**变异策略（从 Java `makeMutatedSearch` 移植）:**
1. 从 5-gram 采样一个 n-gram 片段
2. 推断映射关系
3. 与当前个体的映射合并
4. 冲突则通过 shuffle 解决（最多尝试 100 次）

**Step 1–5:** 实现完整 Solver → 集成测试 → 提交

---

## 阶段五：CLI 集成

### Task 20: CLI 入口 + 编排

**Files:**
- Modify: `decrypto-rs/src/main.rs`
- Modify: `decrypto-rs/src/lib.rs`

**功能:** 完整的 CLI 命令解析和求解编排。

```rust
#[derive(Parser)]
struct Args {
    #[arg(long, default_value = "english-standard.dat")]
    dictionary: String,
    #[arg(short, long, default_value = "")]
    clues: String,
    #[arg(long, default_value_t = 10.0)]
    timelimit: f64,
    #[arg(long, default_value_t = 100)]
    setsize: usize,
    #[arg(long)]
    puzzlefile: Option<String>,
    #[arg(long)]
    scramble: bool,
    #[arg(long, default_value_t = true)]
    trustspaces: bool,
    ciphertext: Option<String>,
}
```

**求解编排:**

```rust
fn run_solver(args: Args) -> Result<()> {
    let dict = Dict::from_file(&args.dictionary)?;
    let ciphertext = /* 从 puzzlefile 或 args.ciphertext */;
    let clues = /* 从 puzzlefile 或 args.clues */;
    let puzzle = Puzzle::new(&dict, &ciphertext, &clues, args.trustspaces, true);

    if args.scramble {
        puzzle.scramble(false);
        println!("{}", puzzle.cipher_text);
        println!("{}", puzzle.clues);
        return Ok(());
    }

    let solver: Box<dyn Solver> = if args.trustspaces {
        Box::new(DictionaryAttackSolver::new(puzzle))
    } else {
        Box::new(GeneticSolver::new(GAProblem::new(puzzle)))
    };

    // 设置监听器，收集解
    // 启动求解
    // 等待完成
    // 输出解
}
```

**Step 1: 集成测试**

```rust
#[test]
fn test_cli_dict_attack() {
    // 运行 CLI 管道的完整流程
    // 从密文+线索到输出解
}

#[test]
fn test_cli_genetic() {
    // trust_spaces=false 的完整流程
}

#[test]
fn test_cli_scramble() {
    // --scramble 模式
}

#[test]
fn test_cli_puzzlefile() {
    // --puzzlefile 读取
}

#[test]
fn test_cli_with_clues() {
    // 已知线索验证求解结果
}
```

**Step 2–5:** 实现完整 CLI → 用 Java 原版的结果对比验证 → 提交

---

## 阶段六：对标验证

### Task 21: 编写已知谜题的端到端测试

**Files:**
- Modify: `tests/integration_test.rs`

从 `helpfile.html` 和原版运行结果中收集测试用例：

```rust
#[test]
fn test_helpfile_example() {
    // helpfile.html 中的例子
    let cipher = "PG XOYHLM XOYLY PZ GH TPUUYLYGRY EYXBYYG XOYHLM WGT JLWRXPRY. PG JLWRXPRY, XOYLY PZ.";
    let clues = "X=T M=Y";
    // 运行求解器
    // 验证明文包含预期单词
}

#[test]
fn test_cipher_with_clues_solves_completely() {
    // 运行字典攻击求解
    // 验证所有词都有映射
}
```

**Step 1–5:** 用原版 Java 运行结果验证 Rust 版本的输出 → 修复不一致 → 提交

---

## 执行顺序总结

```
Task 1: 项目骨架
  └→ Task 2: MapSet
      └→ Task 3: Word/Pattern
          └→ Task 4: Candidate
          └→ Task 5: Language
              ├→ Task 6: Ngram4
              ├→ Task 7: Ngram5+Sampler
              └→ Task 8: Dict
                  └→ Task 9: Solution/SolutionSet
                  └→ Task 10: StopFlag
                  └→ Task 11: Solver traits
                  └→ Task 12: Params/Planners
                      └→ Task 13: Puzzle
                          ├→ Task 14: Finisher
                          ├→ Task 15: SimpleDictionaryAttack
                          │   └→ Task 16: DictionaryAttackSolver
                          └→ Task 17: GAProblem
                              └→ Task 18: GeneticSearch
                                  └→ Task 19: GeneticSolver
                                      └→ Task 20: CLI 集成
                                          └→ Task 21: 对标验证
```

---

## 测试策略

- **单元测试**: 每个模块内部 `#[cfg(test)]`，测试核心逻辑
- **集成测试**: `tests/cli_tests.rs`，使用真实数据文件
- **数据文件**: 所有测试需要在项目根目录下能找到 `java-legacy/` 目录中的数据文件
- **验证方式**: 用 Java 原版运行已知谜题，比较 Rust 版本输出

## 已知关键差异

| Java (原版) | Rust (新版) | 注意点 |
|---|---|---|
| `long[]` 位图, 64bit | `Vec<u64>` | 直接对应 |
| `java.util.Random` | `rand::Rng` | API 不同，需验证种子无关时才一致 |
| `DataInputStream` (大端) | `byteorder::BigEndian` | 二进制文件都是大端 |
| `GZIPInputStream` | `flate2::read::GzDecoder` | 对应 |
| `HashMap` | `HashMap` | 直接对应 |
| `WeakHashMap` (缓存) | `HashMap` + 不要求 Weak | 简化为普通 HashMap |
| `Thread` | `std::thread` | StopFlag 不同实现方式 |
| `TreeList` (自实现BST) | `Vec` + 二分插入 | 性能足够 |
| `synchronized` | `Mutex` / `AtomicBool` | StopFlag 用 AtomicBool |
