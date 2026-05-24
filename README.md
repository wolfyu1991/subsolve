# subsolve

Rust port of **Decrypto 8.5** — a substitution-cipher solver. All data files (dictionary, tetragrams, 5-grams) are embedded at compile time; no runtime dependencies needed.

## Quick start

```bash
cargo build --release
./target/release/subsolve "WKH TXLFN EURZQ IRA"
```

Output:
```
-36.7140 THE QUICK BROWN FOX
Found 1 solutions in 0.02s
Best: -36.7140 THE QUICK BROWN FOX
```

## Usage

```bash
# With clue constraints
subsolve "WKH TXLFN EURZQ IRA" -c "W=T H=H K=E"

# Read puzzle from file
subsolve --puzzlefile puzzle.txt

# Pipe ciphertext from stdin
echo "WKH TXLFN EURZQ IRA" | subsolve

# JSON output
subsolve --json "WKH TXLFN EURZQ IRA"

# Top 3 solutions
subsolve --top 3 "WKH TXLFN EURZQ IRA"

# Genetic solver (no word boundaries)
subsolve --trustspaces false "WKH TXLFN EURZQ IRA"

# Scramble ciphertext for sharing
subsolve --scramble "THE QUICK BROWN FOX"

# Verbose progress
subsolve -v "WKH TXLFN EURZQ IRA"
```

## Options

| Flag | Default | Description |
|------|---------|-------------|
| `--dictionary` | (embedded) | Custom dictionary file |
| `-c`, `--clues` | `""` | Known mappings, e.g. `A=B C!=D` |
| `--timelimit` | `10.0` | Time limit in seconds |
| `--puzzlefile` | — | Read puzzle from file |
| `--scramble` | `false` | Scramble ciphertext for sharing |
| `--trustspaces` | `true` | Treat spaces as word boundaries |
| `--setsize` | `100` | Max solutions to store |
| `--top` | `0` | Show top N solutions (0 = all) |
| `--json` | `false` | JSON output |
| `-v`, `--verbose` | `false` | Progress messages to stderr |
| `--prune-root-node` | `true` | Prune search at root |
| `--prune-each-node` | `true` | Prune search at each node |
| `--enable-single-letters` | `true` | Single-letter mapping hints |
| `--max-reduce-iters` | `50` | Max constraint propagation rounds |
| `--max-random-disable` | `3` | Max words to disable in random search |
| `--max-random-iters` | `1000` | Max random search iterations |

## Solver modes

**`--trustspaces true` (default)** — `DictionaryAttackSolver`
Uses DFS with constraint propagation. Fast, needs words in the dictionary. Works best when spaces are reliable word boundaries.

**`--trustspaces false`** — `GeneticSolver`
Uses a genetic algorithm with n-gram statistics. Slower but handles word-boundary uncertainty.

## Output format

- **stdout**: `score solution` lines (pipe-friendly)
- **stderr**: summary (`Found N solutions in X.XXs`, `Best: score solution`)
- **`--json`**: JSON array to stdout
- **`-v`**: solver status to stderr
- **`--top N`**: limit stdout to N best solutions

## Development

```bash
cargo build              # debug build
cargo build --release    # release build (~5 MB, LTO+strip)
cargo clippy             # lint check (zero warnings target)
cargo test               # run all tests (196 total)
cargo run -- "WKH TXLFN EURZQ IRA"
python tests/bench_test.py --trials 2 --timelimit 10
```

### Requirements

- Rust 1.70+ (edition 2021)
- Data files in `java-legacy/` directory (tracked in git, embedded by `build.rs`)

## Project structure

```
src/
├── main.rs          CLI entrypoint
├── lib.rs           Library root
├── dict.rs          Dictionary binary reader
├── dict_attack.rs   DictionaryAttackSolver (DFS + constraints)
├── finisher.rs      Brute-force finisher for remaining mappings
├── genetic.rs       Genetic algorithm solver
├── language.rs      Language trait + EnglishLanguage
├── mapset.rs        Bitmap mapping set (u64 per letter)
├── ngram4.rs        4-gram language model reader
├── ngram5.rs        5-gram sampler reader
├── params.rs        Solver parameters
├── planner.rs       Word-selection strategies (Lazy/Random)
├── puzzle.rs        Ciphertext parsing, word extraction, clues
├── resources.rs     Embedded data access
├── solution.rs      Solution and SolutionSet (ordered + capped)
├── solver.rs        Solver/SolverListener traits
└── word.rs          Word pattern matching and candidate sorting

tests/
├── cli_tests.rs      23 integration tests
└── bench_test.py     Accuracy benchmark
```
