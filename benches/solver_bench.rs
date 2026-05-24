use criterion::{criterion_group, criterion_main, Criterion, BenchmarkId, BatchSize};

use subsolve::dict::Dict;
use subsolve::dict_attack::DictionaryAttackSolver;
use subsolve::ga_problem::GAProblem;
use subsolve::language::{EnglishLanguage, Language};
use subsolve::mapset::MapSet;
use subsolve::ngram4::Ngram4;
use subsolve::puzzle::Puzzle;
use subsolve::resources;
use subsolve::solver::{Solver, SolverListener};
use subsolve::word::Word;

fn load_dict() -> Dict {
    Dict::from_static(resources::dictionary())
        .expect("embedded dictionary must load")
}

fn build_puzzle(text: &str, trust_spaces: bool) -> Puzzle {
    let dict = load_dict();
    Puzzle::new(dict, text, "", trust_spaces, true)
}

fn make_identity_map() -> Vec<i32> {
    let mut map: Vec<i32> = (0..26).collect();
    map.push(-1); // space (byte 26) is unmapped
    map
}

// ──── Puzzle construction ────────────────────────────────────────────────

fn bench_puzzle_new(c: &mut Criterion) {
    let texts: Vec<(&str, &str)> = vec![
        ("4 words", "THE QUICK BROWN FOX"),
        ("9 words", "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"),
        ("18 words", "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG AND THE SLEEPY DOLPHIN SWIMS FAST UNDER THE BIG WAVE"),
    ];

    let mut group = c.benchmark_group("Puzzle::new");
    for (label, text) in &texts {
        group.bench_with_input(
            BenchmarkId::new("trust_spaces", *label),
            text,
            |b, text| {
                b.iter_batched(
                    load_dict,
                    |dict| Puzzle::new(dict, text, "", true, true),
                    BatchSize::SmallInput,
                );
            },
        );
        group.bench_with_input(
            BenchmarkId::new("no_trust_spaces", *label),
            text,
            |b, text| {
                b.iter_batched(
                    load_dict,
                    |dict| Puzzle::new(dict, text, "", false, true),
                    BatchSize::SmallInput,
                );
            },
        );
    }
    group.finish();
}

// ──── MapSet operations ──────────────────────────────────────────────────

fn bench_mapset_intersect_with(c: &mut Criterion) {
    let mut a = MapSet::new(26);
    let mut b = MapSet::new(26);
    a.set_full_set();
    b.set_full_set();
    for i in 0..13 {
        b.forbid_mapping(i as u8, i as u8);
    }

    c.bench_function("MapSet::intersect_with", |bench| {
        bench.iter_batched(
            || a.clone(),
            |mut a| a.intersect_with(&b),
            BatchSize::SmallInput,
        );
    });
}

fn bench_mapset_self_reduce(c: &mut Criterion) {
    let mut ms = MapSet::new(26);
    for i in 0..26 {
        ms.set_mapping(i as u8, (i as u8 + 13) % 26);
    }
    ms.set[0] = (1u64 << 0) | (1u64 << 1);
    ms.set[1] = (1u64 << 1) | (1u64 << 2);

    c.bench_function("MapSet::self_reduce", |bench| {
        bench.iter_batched(
            || ms.clone(),
            |mut ms| ms.self_reduce(),
            BatchSize::SmallInput,
        );
    });
}

fn bench_mapset_set_mapping(c: &mut Criterion) {
    c.bench_function("MapSet::set_mapping", |bench| {
        let mut ms = MapSet::new(26);
        ms.set_full_set();
        bench.iter(|| {
            ms.set_mapping(0, 5);
            ms.set_mapping(0, 0);
        });
    });
}

// ──── Ngram4 get_log_prob ────────────────────────────────────────────────

fn bench_ngram4_get_log_prob(c: &mut Criterion) {
    let ngram4 = Ngram4::from_raw(resources::tetragrams_raw());
    let mut group = c.benchmark_group("Ngram4::get_log_prob");
    group.bench_function("sequential_calls", |bench| {
        bench.iter(|| {
            let mut sum = 0.0f64;
            for a in 0..28u8 {
                for b in 0..28u8 {
                    for c in 0..28u8 {
                        sum += ngram4.get_log_prob(a, b, c, 0);
                    }
                }
            }
            sum
        });
    });
    group.finish();
}

// ──── Score computation ──────────────────────────────────────────────────

fn bench_compute_score_map(c: &mut Criterion) {
    let lang = EnglishLanguage::new();
    let identity = make_identity_map();

    let mut group = c.benchmark_group("Language::compute_score_map");
    for (label, text) in [("short", "A".repeat(20)), ("medium", "A".repeat(100)), ("long", "A".repeat(500))] {
        let bytes = lang.cipher_text_to_cipher_bytes(&text);
        group.bench_with_input(
            BenchmarkId::new(label, bytes.len()),
            &bytes,
            |b, bytes| {
                b.iter(|| lang.compute_score_map(bytes, &identity));
            },
        );
    }
    group.finish();
}

fn bench_ga_compute_score(c: &mut Criterion) {
    let puzzle = build_puzzle("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG", false);
    let problem = GAProblem::new(puzzle);
    let identity = make_identity_map();

    c.bench_function("GAProblem::compute_score", |bench| {
        bench.iter(|| problem.compute_score(&identity));
    });
}

fn bench_compute_score_delta(c: &mut Criterion) {
    let puzzle = build_puzzle("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG", false);
    let problem = GAProblem::new(puzzle);
    let map = make_identity_map();

    let affected: Vec<usize> = (0..problem.puzzle.cipher_bytes.len())
        .filter(|&i| {
            let cb = problem.puzzle.cipher_bytes[i];
            cb == 4 || cb == 19
        })
        .collect();

    let mut group = c.benchmark_group("GAProblem::compute_score_delta");
    group.bench_function("swap_E_T", |bench| {
        bench.iter(|| problem.compute_score_delta(&map, 4, 19, &affected));
    });

    let q_affected: Vec<usize> = (0..problem.puzzle.cipher_bytes.len())
        .filter(|&i| problem.puzzle.cipher_bytes[i] == 16 || problem.puzzle.cipher_bytes[i] == 25)
        .collect();
    group.bench_function("swap_Q_Z", |bench| {
        bench.iter(|| problem.compute_score_delta(&map, 16, 25, &q_affected));
    });
    group.finish();
}

// ──── Genetic search iterate_with_sa ─────────────────────────────────────

fn bench_iterate_with_sa(c: &mut Criterion) {
    use std::sync::atomic::AtomicBool;
    use std::sync::Arc;

    let puzzle = build_puzzle("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG", false);
    let problem = GAProblem::new(puzzle);
    use subsolve::genetic_search::GeneticSearch;
    use rand::rngs::StdRng;
    use rand::SeedableRng;

    let stop_flag = Arc::new(AtomicBool::new(false));

    struct NoopListener;
    impl SolverListener for NoopListener {
        fn on_message(&mut self, _: &str) {}
        fn on_progress(&mut self, _: f64) {}
        fn on_finished(&mut self, _: f64) {}
        fn on_solution(&mut self, _: &MapSet, _: Option<Vec<i32>>, _: bool, _: f64) {}
    }

    let listeners = &mut [Box::new(NoopListener) as Box<dyn SolverListener>];

    c.bench_function("GeneticSearch::iterate_with_sa_1000", |bench| {
        bench.iter_batched(
            || {
                let mut rng = StdRng::seed_from_u64(42);
                let search = GeneticSearch::new(&problem, Arc::clone(&stop_flag), &mut rng);
                (search, rng)
            },
            |(mut search, mut rng)| {
                search.iterate_with_sa(1000, None, f32::NEG_INFINITY, &mut rng, listeners, None, 0.999);
            },
            BatchSize::SmallInput,
        );
    });
}

// ──── Dict lookup ────────────────────────────────────────────────────────

fn bench_dict_lookup(c: &mut Criterion) {
    let mut dict = load_dict();

    let patterns: Vec<(&str, Vec<u8>)> = vec![
        ("THE", Word::make_pattern(b"THE")),
        ("QUICK", Word::make_pattern(b"QUICK")),
        ("BROWN", Word::make_pattern(b"BROWN")),
        ("JUMPS", Word::make_pattern(b"JUMPS")),
        ("OVER", Word::make_pattern(b"OVER")),
        ("LAZY", Word::make_pattern(b"LAZY")),
        ("DOG", Word::make_pattern(b"DOG")),
        ("CRYPTOGRAM", Word::make_pattern(b"CRYPTOGRAM")),
    ];

    let mut group = c.benchmark_group("Dict::lookup");
    for (name, pattern) in &patterns {
        group.bench_with_input(
            BenchmarkId::new(*name, pattern.len()),
            pattern,
            |b, pat| {
                b.iter(|| dict.lookup(pat));
            },
        );
    }
    group.finish();
}

// ──── Find spaces4 ───────────────────────────────────────────────────────

fn bench_find_spaces4(c: &mut Criterion) {
    use subsolve::genetic_search::find_spaces4;

    let puzzle = build_puzzle("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG AND THE SLEEPY DOLPHIN SWIMS FAST UNDER THE BIG WAVE THAT CARRIES THE BOAT ACROSS THE WIDE OCEAN", false);
    let cipher_bytes = puzzle.cipher_bytes.clone();
    let identity = make_identity_map();

    c.bench_function("find_spaces4_long_text", |bench| {
        bench.iter(|| find_spaces4(&puzzle, &cipher_bytes, &identity));
    });

    let puzzle_short = build_puzzle("THE QUICK BROWN FOX", false);
    let cipher_bytes_short = puzzle_short.cipher_bytes.clone();
    c.bench_function("find_spaces4_short_text", |bench| {
        bench.iter(|| find_spaces4(&puzzle_short, &cipher_bytes_short, &identity));
    });
}

// ──── Full solver start ──────────────────────────────────────────────────

fn bench_solver_start(c: &mut Criterion) {
    let ciphertexts: Vec<(&str, &str)> = vec![
        ("short", "WKH TXLFN EURZQ IRA"),
        ("long", "WKH TXLFN EURZQ IRA MXPSV RYHU WKH ODCB GRJ"),
    ];

    struct NullListener;
    impl SolverListener for NullListener {
        fn on_message(&mut self, _: &str) {}
        fn on_progress(&mut self, _: f64) {}
        fn on_finished(&mut self, _: f64) {}
        fn on_solution(&mut self, _: &MapSet, _: Option<Vec<i32>>, _: bool, _: f64) {}
    }

    let mut group = c.benchmark_group("DictionaryAttackSolver::start");
    for (label, text) in &ciphertexts {
        group.bench_with_input(
            BenchmarkId::new(*label, text.len()),
            text,
            |b, text| {
                b.iter_batched(
                    || {
                        let dict = load_dict();
                        let puzzle = Puzzle::new(dict, text, "", true, true);
                        let mut solver = DictionaryAttackSolver::new(puzzle);
                        solver.add_listener(Box::new(NullListener));
                        solver
                    },
                    |mut solver| {
                        solver.start(2.0);
                    },
                    BatchSize::SmallInput,
                );
            },
        );
    }
    group.finish();
}

criterion_group!(
    benches,
    bench_puzzle_new,
    bench_mapset_intersect_with,
    bench_mapset_self_reduce,
    bench_mapset_set_mapping,
    bench_ngram4_get_log_prob,
    bench_compute_score_map,
    bench_ga_compute_score,
    bench_compute_score_delta,
    bench_iterate_with_sa,
    bench_dict_lookup,
    bench_find_spaces4,
    bench_solver_start,
);
criterion_main!(benches);
