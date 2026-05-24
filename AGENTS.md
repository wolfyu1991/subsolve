# subsolve — Decrypto 8.5 Rust port

## Repo layout

```
Cargo.toml              # Rust package manifest
build.rs                # Build script (embeds data files at compile time)
.gitignore
AGENTS.md               # this file
DECRYPTO_ANALYSIS.md    # decompiled Java analysis (generated 2026-05-22)

java-legacy/            # original Java project + data files
├── decrypto.jar
├── english-standard.dat    # dictionary (1.5 MB, custom binary)
├── english4.sta            # 4-gram with spaces (GZIP float[28][28][28][28])
├── english4ns.sta          # 4-gram without spaces
├── gramfreq5.stb           # 5-gram sampler (GZIP)
├── gramfreq5ns.stb
└── src/decrypto/           # decompiled Java sources (42 files, package `decrypto`)

src/                    # Rust source (22 files)
├── main.rs             # CLI entrypoint
├── lib.rs              # library root
├── resources.rs        # embedded data access via include_bytes!
├── dict.rs, dict_attack.rs, simple_dict_attack.rs, finisher.rs
├── ga_problem.rs       # GAProblem (score precomputation)
├── genetic_search.rs   # GeneticSearch (hill-climbing search)
├── genetic.rs          # GeneticSolver (population management)
├── language.rs, mapset.rs, ngram4.rs, ngram5.rs
├── params.rs, planner.rs, puzzle.rs
├── solution.rs, solver.rs, word.rs

tests/
├── cli_tests.rs        # 23 integration tests (CLI flags, known-answer ciphers, genetic solver, clues, JSON, top, setsize)
├── prop_tests.rs       # 9 property-based tests (MapSet, word patterns, language round-trips)
├── round_trip.rs       # 2 round-trip tests (clue-enforced, valid-output check)
└── bench_test.py       # Python benchmark: generates random cipher keys, tests accuracy across sentence lengths

docs/plans/
└── 2026-05-22-rust-cli-port.md
```

## Entrypoints

| Mode | Class | How to run |
|---|---|---|
| CLI (Rust) | `main.rs` | `cargo run -- [options] [ciphertext]` |
| CLI (binary) | — | `subsolve.exe [options] [ciphertext]` |
| GUI (original Java) | `decrypto.gui.DecryptoGUI` | `java -jar decrypto.jar` |
| CLI (original Java) | `decrypto.DecryptoCGI` | `java -jar decrypto.jar [options] [ciphertext]` |

CLI options: `--dictionary`, `-c`/`--clues`, `--timelimit`, `--puzzlefile`, `--scramble`, `--trustspaces`, `--setsize`, `--top`, `--json`, `-v`/`--verbose`, `--prune-root-node`, `--prune-each-node`, `--enable-single-letters`, `--max-reduce-iters`, `--max-random-disable`, `--max-random-iters`

## Solver decision logic

- `--trustspaces true` (default) → `DictionaryAttackSolver` (fast, needs words in dict)
- `--trustspaces false` → `GeneticSolver` (slow, uses n-gram stats)
- Solver parameters (`--prune-root-node`, `--prune-each-node`, `--enable-single-letters`, `--max-reduce-iters`, `--max-random-disable`, `--max-random-iters`) passed via `DecryptoParameters` struct (`params.rs`)

## Output format

- stdout: `score solution` lines (pipe-friendly)
- stderr: summary line (`Found N solutions in X.XXs`, `Best: score solution`)
- `--json`: JSON array to stdout instead of text lines
- `--top N`: limit stdout output to N best solutions
- `-v`/`--verbose`: progress messages and solver status to stderr

## Key data structures

- **`DecryptoParameters`** (`params.rs`): solver tuning — pruning, iterations, word-disable counts.
- **`Planner`** trait + `LazyPlanner` / `RandomPlanner` (`planner.rs`): choose which word to solve next.
- **`MapSet`** (`u64[]` bitmaps): each cipher letter's possible plain mappings as bits. `oneHotLog2()` uses 256-entry lookup for O(1) unique mapping decode.
- **`Dict`** binary format: magic `0xDEC0DEC5` → props → 0xFF-separated records with `wordlen`, `nwords`, word bytes. Lookup by pattern via binary search + `WeakHashMap` cache. Now in-memory (`Vec<u8>` + `Cursor`).

## Build

```bash
cargo build              # debug
cargo build --release    # single binary, ~5 MB with LTO+strip
cargo clippy             # lint check (zero warnings)
cargo test               # 207 tests (173 unit + 23 CLI + 9 proptest + 2 round-trip)
cargo bench --bench solver_bench  # criterion benchmarks (~2-5 min)
cargo run -- "WKH TXLFN EURZQ IRA"
python tests/bench_test.py --trials 2 --timelimit 10  # accuracy benchmark
```

No external data files needed at runtime — dictionary, tetragrams, and 5-grams are embedded.

## Benchmarks (release build, Ubuntu)

| Benchmark | Time |
|-----------|------|
| `Puzzle::new` (4 words) | ~1.6 ms |
| `Puzzle::new` (18 words) | ~6.5 ms |
| `Language::compute_score_map` (20 chars) | 58 ns |
| `Language::compute_score_map` (500 chars) | 1.37 µs |
| `GAProblem::compute_score` (full sentence) | 111 ns |
| `GAProblem::compute_score_delta` (swap E↔T) | 43 ns |
| `GeneticSearch::iterate_with_sa` (1000 iters) | 663 µs |
| `MapSet::intersect_with` | 66 ns |
| `MapSet::self_reduce` | 184 ns |
| `MapSet::set_mapping` | 17 ns |
| `find_spaces4` (80 chars) | 15 µs |
| `Dict::lookup` (3-letter) | 40 µs |
| `Dict::lookup` (5-letter) | 358 µs |
| `Ngram4::get_log_prob` (21k sequential) | 18.8 µs |

## CI

GitHub Actions workflows in `.github/workflows/ci.yml`:
- **Clippy** — lint check on Ubuntu
- **Test** — full test suite on Ubuntu, Windows, macOS
- **Build release** — produces binary artifacts for all 3 platforms
- **Benchmark (Python)** — accuracy benchmark via `bench_test.py`
- **Benchmark (Criterion)** — criterion benchmarks + uploads HTML report

## Test suite (207 tests)

| Suite | Count | File(s) |
|-------|-------|---------|
| Unit tests | 173 | `src/*.rs` (inline `#[cfg(test)]` modules) |
| CLI integration | 23 | `tests/cli_tests.rs` |
| Property (proptest) | 9 | `tests/prop_tests.rs` |
| Round-trip | 2 | `tests/round_trip.rs` |

Run: `cargo test` (all), or filter by suite name.

## Key refactoring history

- **`dict_attack.rs` split**: `SimpleDictionaryAttack` extracted to `simple_dict_attack.rs` (May 2026)
- **`genetic.rs` split**: `GAProblem` → `ga_problem.rs`, `GeneticSearch` → `genetic_search.rs` (May 2026)
- **Performance**: `find_spaces4` flat buffer allocation, `make_initial_map` retry limit to prevent hangs
- **Safety**: All `unwrap()` calls hardened with `expect()` + justification comments
- **Error handling**: `main()` returns `Result<(), Box<dyn Error>>` instead of `process::exit()`

## Requirements

- Rust 1.70+ (edition 2021)
- Java 8+ only required to run the original `decrypto.jar`
- Decrypted analysis in `DECRYPTO_ANALYSIS.md`
- Java source in `java-legacy/src/decrypto/` is decompiled (CFR 0.152)
