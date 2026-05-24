use std::collections::HashMap;
use std::sync::atomic::Ordering;
use std::sync::OnceLock;

use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};

pub use crate::ga_problem::GAProblem;
use crate::genetic_search::GeneticSearch;
use crate::ngram5::{make_pattern, Sampler};
use crate::resources;
use crate::solver::{Solver, SolverListener, StopFlag};

static GRAMFREQ_WS: OnceLock<HashMap<String, Sampler<String>>> = OnceLock::new();
static GRAMFREQ_NS: OnceLock<HashMap<String, Sampler<String>>> = OnceLock::new();

fn gramfreq_ws() -> &'static HashMap<String, Sampler<String>> {
    GRAMFREQ_WS
        .get_or_init(|| read_ngram5_from_bytes(resources::gramfreq_ws()).expect("Failed to load gramfreq5.stb"))
}

fn gramfreq_ns() -> &'static HashMap<String, Sampler<String>> {
    GRAMFREQ_NS
        .get_or_init(|| read_ngram5_from_bytes(resources::gramfreq_ns()).expect("Failed to load gramfreq5ns.stb"))
}

fn read_ngram5_from_bytes(data: &[u8]) -> Result<HashMap<String, Sampler<String>>, std::io::Error> {
    crate::ngram5::read_ngram5_from_bytes(data)
}

pub struct GeneticSolver {
    pub problem: GAProblem,
    pub listeners: Vec<Box<dyn SolverListener>>,
    pub start_time: f64,
    pub end_time: f64,
    pub stop_flag: Option<StopFlag>,
    pub rng: StdRng,
    pub sa_temperature: Option<f32>,
    pub sa_cooling: f32,
}

const PARALLEL_BATCH: usize = 4;

impl GeneticSolver {
    pub fn new(problem: GAProblem) -> Self {
        GeneticSolver {
            problem,
            listeners: Vec::new(),
            start_time: 0.0,
            end_time: 0.0,
            stop_flag: None,
            rng: StdRng::from_entropy(),
            sa_temperature: Some(1.0),
            sa_cooling: 0.999,
        }
    }

    fn current_time() -> f64 {
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs_f64()
    }

    fn similarity(mapa: &[i32], mapb: &[i32], histogram: &[i32], num_unique: usize) -> f64 {
        let mut same = 0;
        for i in 0..histogram.len() {
            if histogram[i] > 0 && mapa[i] == mapb[i] {
                same += 1;
            }
        }
        same as f64 / num_unique as f64
    }

    fn make_mutated_search<'a>(
        parent: &GeneticSearch<'a>,
        rng: &mut StdRng,
        gramfreq: &HashMap<String, Sampler<String>>,
    ) -> Option<GeneticSearch<'a>> {
        let problem = parent.problem;
        let n_permutes = 1 + rng.gen_range(0..problem.puzzle.lang.letter_count());
        let mut new_search = parent.mutate(n_permutes, rng)?;

        let key_len = gramfreq.keys().next()?.len();
        if problem.puzzle.cipher_bytes.len() <= key_len {
            return None;
        }

        let offset = rng.gen_range(0..problem.puzzle.cipher_bytes.len() - key_len);
        let cipher_snippet: Vec<u8> =
            problem.puzzle.cipher_bytes[offset..offset + key_len].to_vec();

        let pattern = make_pattern(&problem.puzzle.lang.bytes_to_string(&cipher_snippet));
        let sampler = gramfreq.get(&pattern)?;

        let plain_snippet_string = sampler.sample(rng.gen::<f64>());
        let plain_snippet = problem.puzzle.lang.string_to_bytes(plain_snippet_string);

        let mut new_cipher_bytes = vec![0u8; key_len];
        let mut new_plain_bytes = vec![0u8; key_len];
        let mut n_new_bytes = 0usize;

        for i in 0..cipher_snippet.len() {
            let cb = cipher_snippet[i];
            let pb = plain_snippet[i];
            if pb == problem.puzzle.lang.letter_count() as u8 {
                continue;
            }
            let mut already = false;
            if new_cipher_bytes[..n_new_bytes].contains(&cb) {
                already = true;
            }
            if already {
                continue;
            }
            new_cipher_bytes[n_new_bytes] = cb;
            new_plain_bytes[n_new_bytes] = pb;
            n_new_bytes += 1;
            if !problem.puzzle.initial_map.is_mapping_ok(cb, pb) {
                return None;
            }
        }

        let mut cipher_orphans = vec![0u8; key_len];
        let mut n_cipher_orphans = 0usize;
        let mut plain_orphans = vec![0u8; key_len];
        let mut n_plain_orphans = 0usize;

        let new_map = new_search.map.as_ref()?;

        for cbidx in 0..n_new_bytes {
            let mut cipher_orphan: Option<u8> = None;
            for j in 0..problem.puzzle.lang.letter_count() as u8 {
                if new_map[j as usize] == new_plain_bytes[cbidx] as i32 {
                    cipher_orphan = Some(j);
                    break;
                }
            }
            if let Some(co) = cipher_orphan {
                let mut orphan = true;
                if new_cipher_bytes[..n_new_bytes].contains(&co) {
                    orphan = false;
                }
                if orphan {
                    cipher_orphans[n_cipher_orphans] = co;
                    n_cipher_orphans += 1;
                }
            }

            let plain_orphan = new_map[new_cipher_bytes[cbidx] as usize] as u8;
            let mut orphan2 = true;
            if new_plain_bytes[..n_new_bytes].contains(&plain_orphan) {
                orphan2 = false;
            }
            if orphan2 {
                plain_orphans[n_plain_orphans] = plain_orphan;
                n_plain_orphans += 1;
            }
        }

        if n_cipher_orphans != n_plain_orphans {
            return None;
        }

        let mut shuffle_trials = 0;
        let shuffle_ok = loop {
            GeneticSearch::shuffle(rng, &mut cipher_orphans[..n_cipher_orphans]);
            let mut bad = false;
            for i in 0..n_cipher_orphans {
                if !problem
                    .puzzle
                    .initial_map
                    .is_mapping_ok(cipher_orphans[i], plain_orphans[i])
                {
                    bad = true;
                    break;
                }
            }
            if !bad {
                break true;
            }
            shuffle_trials += 1;
            if shuffle_trials > 100 {
                break false;
            }
        };

        if !shuffle_ok {
            return None;
        }

        let new_map = new_search.map.as_mut()?;
        for i in 0..n_cipher_orphans {
            new_map[cipher_orphans[i] as usize] = plain_orphans[i] as i32;
        }
        for i in 0..n_new_bytes {
            new_map[new_cipher_bytes[i] as usize] = new_plain_bytes[i] as i32;
        }

        Some(new_search)
    }

    fn run_solver(&mut self) {
        // run_solver called from start() which sets stop_flag = Some(...) just before
        let stop_flag = self.stop_flag.as_ref()
            .expect("run_solver: stop_flag must be set before calling run_solver");

        let gramfreq = if self.problem.puzzle.trust_spaces {
            gramfreq_ws()
        } else {
            gramfreq_ns()
        };

        let mut best_searches: Vec<GeneticSearch<'_>> = Vec::new();
        let mut score_thresh = f32::NEG_INFINITY;

        let mut last_prog = -1.0;
        loop {
            if stop_flag.should_stop() {
                break;
            }

            let prog = stop_flag.progress();
            if prog - last_prog >= 0.05 {
                last_prog = prog;
                for listener in &mut self.listeners {
                    listener.on_progress(prog);
                }
            }

            let mut batch: Vec<Option<GeneticSearch<'_>>> = (0..PARALLEL_BATCH)
                .map(|_| {
                    if best_searches.is_empty() {
                        Some(GeneticSearch::new(
                            &self.problem,
                            stop_flag.stop.clone(),
                            &mut self.rng,
                        ))
                    } else {
                        let r = self.rng.gen_range(0..3);
                        match r {
                            0 => {
                                let idx = self.rng.gen_range(0..best_searches.len());
                                let parent = &best_searches[idx];
                                Self::make_mutated_search(parent, &mut self.rng, gramfreq)
                            }
                            1 => {
                                let idx = self.rng.gen_range(0..best_searches.len());
                                let parent = &best_searches[idx];
                                let n = self.rng
                                    .gen_range(1..=self.problem.puzzle.lang.letter_count() / 2);
                                parent.mutate(n, &mut self.rng)
                            }
                            _ => Some(GeneticSearch::new(
                                &self.problem,
                                stop_flag.stop.clone(),
                                &mut self.rng,
                            )),
                        }
                    }
                })
                .collect();

            batch.retain(|gs| gs.is_some());
            if batch.is_empty() {
                continue;
            }

            let n_batch = batch.len();
            let mut rngs: Vec<StdRng> = (0..n_batch)
                .map(|_| StdRng::seed_from_u64(self.rng.gen()))
                .collect();
            let sa_temp = self.sa_temperature;
            let sa_cool = self.sa_cooling;

            let mut batch_results: Vec<GeneticSearch<'_>> = std::thread::scope(|s| {
                let mut handles = Vec::with_capacity(n_batch);
                for (gs_opt, thread_rng) in batch.iter_mut().zip(rngs.iter_mut()) {
                    // batch filtered to retain only Some values above
                    let mut gs = gs_opt.take()
                        .expect("run_solver: batch element must be Some after retain");
                    handles.push(s.spawn(move || {
                        gs.iterate_with_sa(
                            10000,
                            None,
                            score_thresh,
                            thread_rng,
                            &mut [],
                            sa_temp,
                            sa_cool,
                        );
                        gs
                    }));
                }
                handles.into_iter().map(|h| h.join().unwrap()).collect()
            });

            for gs in &batch_results {
                if gs.score > score_thresh {
                    // iterate_with_sa always restores self.map to Some before returning
                    gs.report_solution(
                        gs.map.as_ref()
                            .expect("run_solver: iterate_with_sa must restore map"),
                        gs.score,
                        &mut self.listeners,
                    );
                }
            }

            for gs in batch_results.drain(..) {
                score_thresh = score_thresh.max(gs.score);

                if best_searches.is_empty() {
                    best_searches.push(gs);
                    continue;
                }

                best_searches.push(gs);

                let n = best_searches.len();
                let mut to_remove = vec![false; n];
                let hist = &self.problem.puzzle.histogram;
                let n_unq = self.problem.puzzle.num_unique_ciphertext_letters;
                for i in 0..n {
                    for j in 0..i {
                        if to_remove[i] || to_remove[j] {
                            continue;
                        }
                        let map_i = match best_searches[i].map.as_ref() {
                            Some(m) => m,
                            None => continue,
                        };
                        let map_j = match best_searches[j].map.as_ref() {
                            Some(m) => m,
                            None => continue,
                        };
                        if Self::similarity(map_i, map_j, hist, n_unq) > 0.65 {
                            if best_searches[i].score > best_searches[j].score {
                                to_remove[j] = true;
                            } else {
                                to_remove[i] = true;
                            }
                        }
                    }
                }

                let mut filtered: Vec<GeneticSearch<'_>> = Vec::with_capacity(n);
                for (i, gs_iter) in best_searches.drain(..).enumerate() {
                    if !to_remove[i] {
                        filtered.push(gs_iter);
                    }
                }
                best_searches = filtered;

                if best_searches.len() > 15 {
                    let mut worst_idx = 0;
                    let mut worst_score = best_searches[0].score;
                    for (i, bs) in best_searches.iter().enumerate().skip(1) {
                        if bs.score < worst_score {
                            worst_score = bs.score;
                            worst_idx = i;
                        }
                    }
                    best_searches.swap_remove(worst_idx);
                }
            }
        }
    }
}

impl Solver for GeneticSolver {
    fn add_listener(&mut self, listener: Box<dyn SolverListener>) {
        self.listeners.push(listener);
    }

    fn start(&mut self, max_time: f64) {
        self.stop_flag = Some(StopFlag::new(max_time));
        self.start_time = Self::current_time();
        self.end_time = 0.0;

        for listener in &mut self.listeners {
            listener.on_message("Searching...");
        }

        self.run_solver();

        self.end_time = Self::current_time();
        let elapsed = self.end_time - self.start_time;
        for listener in &mut self.listeners {
            listener.on_finished(elapsed);
        }
    }

    fn stop(&mut self) {
        if let Some(ref flag) = self.stop_flag {
            flag.stop.store(true, Ordering::SeqCst);
        }
        self.end_time = Self::current_time();
    }

    fn elapsed_time(&self) -> f64 {
        if self.end_time != 0.0 {
            return self.end_time - self.start_time;
        }
        if self.start_time == 0.0 {
            return 0.0;
        }
        Self::current_time() - self.start_time
    }
}

#[cfg(test)]
mod solver_tests {
    use super::*;
    use crate::dict::Dict;
    use crate::puzzle::Puzzle;

    fn make_test_puzzle() -> Puzzle {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        Puzzle::new(dict, "ABC DEF", "", false, true)
    }

    #[test]
    fn test_genetic_solver_new() {
        let puzzle = make_test_puzzle();
        let ga = GAProblem::new(puzzle);
        let solver = GeneticSolver::new(ga);
        assert!(solver.listeners.is_empty());
        assert!((solver.elapsed_time() - 0.0).abs() < 1e-10);
    }

    #[test]
    fn test_similarity_identical() {
        let puzzle = make_test_puzzle();
        let ga = GAProblem::new(puzzle);
        let solver = GeneticSolver::new(ga);
        let n = solver.problem.puzzle.lang.letter_count();
        let mut map = vec![0i32; n + 1];
        for i in 0..n {
            map[i] = i as i32;
        }
        map[n] = n as i32;
        let hist = &solver.problem.puzzle.histogram;
        let n_unq = solver.problem.puzzle.num_unique_ciphertext_letters;
        let sim = GeneticSolver::similarity(&map, &map, hist, n_unq);
        assert!((sim - 1.0).abs() < 1e-10);
    }

    #[test]
    fn test_similarity_different() {
        let puzzle = make_test_puzzle();
        let ga = GAProblem::new(puzzle);
        let n = ga.puzzle.lang.letter_count();
        let mut map1 = vec![0i32; n + 1];
        let mut map2 = vec![0i32; n + 1];
        for i in 0..n {
            map1[i] = i as i32;
            map2[i] = ((i + 1) % n) as i32;
        }
        map1[n] = n as i32;
        map2[n] = n as i32;
        let solver = GeneticSolver::new(ga);
        let hist = &solver.problem.puzzle.histogram;
        let n_unq = solver.problem.puzzle.num_unique_ciphertext_letters;
        let sim = GeneticSolver::similarity(&map1, &map2, hist, n_unq);
        assert!(sim >= 0.0 && sim <= 1.0);
    }

    #[test]
    fn test_gramfreq_loaded() {
        let freq = gramfreq_ws();
        assert!(!freq.is_empty());
    }

    #[test]
    fn test_gramfreq_ns_loaded() {
        let freq = gramfreq_ns();
        assert!(!freq.is_empty());
    }
}
