use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use rand::rngs::StdRng;
use rand::Rng;

use crate::ga_problem::GAProblem;
use crate::mapset::MapSet;
use crate::puzzle::Puzzle;
use crate::solver::SolverListener;

#[derive(Clone, Debug)]
struct LetterChoice {
    cipher_letter: usize,
    n_choices: usize,
}

impl PartialEq for LetterChoice {
    fn eq(&self, other: &Self) -> bool {
        self.n_choices == other.n_choices
    }
}

impl Eq for LetterChoice {}

impl PartialOrd for LetterChoice {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for LetterChoice {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        self.n_choices.cmp(&other.n_choices)
    }
}

pub struct GeneticSearch<'a> {
    pub problem: &'a GAProblem,
    pub map: Option<Vec<i32>>,
    pub score: f32,
    pub stop_flag: Arc<AtomicBool>,
    pub swaps: Vec<i32>,
    affected_bm: Vec<bool>,
    affected: Vec<usize>,
}

impl<'a> GeneticSearch<'a> {
    pub fn new(
        problem: &'a GAProblem,
        stop_flag: Arc<AtomicBool>,
        rng: &mut StdRng,
    ) -> Self {
        let swaps = Self::make_swaps(problem.puzzle.lang.letter_count());
        let len = problem.puzzle.cipher_bytes.len();
        let mut gs = GeneticSearch {
            problem,
            map: None,
            score: f32::NEG_INFINITY,
            stop_flag,
            swaps,
            affected_bm: vec![false; len],
            affected: Vec::with_capacity(len),
        };
        gs.make_initial_map(rng);
        gs
    }

    fn make_initial_map(&mut self, rng: &mut StdRng) -> bool {
        let n = self.problem.puzzle.lang.letter_count();
        let mut map = vec![0i32; n + 1];
        map[n] = n as i32;

        let mut choices: Vec<LetterChoice> = Vec::with_capacity(n);
        for i in 0..n {
            let mut lc = LetterChoice {
                cipher_letter: i,
                n_choices: 0,
            };
            for j in 0..n {
                if self.problem.puzzle.initial_map.is_mapping_ok(i as u8, j as u8) {
                    lc.n_choices += 1;
                }
            }
            choices.push(lc);
        }
        choices.sort();

        let max_retries = 1000;
        let mut retries = 0;
        let mut bad_init = true;
        while bad_init && retries < max_retries {
            retries += 1;
            if self.stop_flag.load(Ordering::Relaxed) {
                return false;
            }

            let mut mapped = vec![false; n];
            bad_init = false;

            for choice in choices.iter().take(n) {
                let ci = choice.cipher_letter;
                let mut possibilities = Vec::with_capacity(n);
                for (j, &m) in mapped.iter().enumerate().take(n) {
                    if self.problem.puzzle.initial_map.is_mapping_ok(ci as u8, j as u8)
                        && !m
                    {
                        possibilities.push(j);
                    }
                }

                if possibilities.is_empty() {
                    bad_init = true;
                    break;
                }

                let pi = possibilities[rng.gen_range(0..possibilities.len())];
                map[ci] = pi as i32;
                mapped[pi] = true;
            }
        }

        if bad_init {
            // fallback: assign identity mapping (may produce invalid results,
            // but allows the search to start rather than hanging indefinitely)
            for (i, m) in map.iter_mut().enumerate().take(n) {
                *m = i as i32;
            }
        }

        self.map = Some(map);
        true
    }

    pub fn report_solution(
        &self,
        map: &[i32],
        score: f32,
        listeners: &mut [Box<dyn SolverListener>],
    ) {
        let mapset = MapSet::new_from_map(map);
        let spaces = if !self.problem.puzzle.trust_spaces {
            Some(find_spaces4(
                &self.problem.puzzle,
                &self.problem.puzzle.cipher_bytes,
                map,
            ))
        } else {
            None
        };
        let norm_score = score / self.problem.puzzle.num_ciphertext_letters as f32;
        for listener in listeners.iter_mut() {
            listener.on_solution(&mapset, spaces.clone(), false, norm_score as f64);
        }
    }

    pub fn shuffle<T>(rng: &mut StdRng, v: &mut [T]) {
        let len = v.len();
        for i in 0..len {
            let j = rng.gen_range(i..len);
            v.swap(i, j);
        }
    }

    pub fn mutate(&self, npermutes: usize, rng: &mut StdRng) -> Option<GeneticSearch<'a>> {
        let mut gas = GeneticSearch::new(self.problem, self.stop_flag.clone(), rng);
        gas.map.as_ref()?;
        let src_map = self.map.as_ref()?;
        let dst_map = gas.map.as_mut()
            .expect("mutate: gas.map set to Some by GeneticSearch::new above");
        dst_map.copy_from_slice(src_map);

        let n = self.problem.puzzle.lang.letter_count();
        let mut cletters = [0usize; 26];
        let mut pletters = [0i32; 26];
        let mut cletters_picked = [false; 28];

        for i in 0..npermutes {
            let idx = loop {
                let r = rng.gen_range(0..n);
                if !cletters_picked[r] {
                    break r;
                }
            };
            cletters_picked[idx] = true;
            cletters[i] = idx;
            pletters[i] = src_map[idx];
        }

        let mut trials = 0;
        let success = loop {
            Self::shuffle(rng, &mut cletters[..npermutes]);
            let mut bad = false;
            for i in 0..npermutes {
                if !self
                    .problem
                    .puzzle
                    .initial_map
                    .is_mapping_ok(cletters[i] as u8, pletters[i] as u8)
                {
                    bad = true;
                    break;
                }
            }
            if !bad {
                break true;
            }
            trials += 1;
            if trials > 1000 {
                break false;
            }
        };

        if !success {
            return None;
        }

        for i in 0..npermutes {
            dst_map[cletters[i]] = pletters[i];
        }

        Some(gas)
    }

    fn make_swaps(n: usize) -> Vec<i32> {
        let mut swaps = Vec::with_capacity(n * (n - 1));
        for i in 0..n {
            for j in 0..n {
                if i != j {
                    swaps.push(((i as i32) << 16) | (j as i32));
                }
            }
        }
        swaps
    }

    #[allow(clippy::too_many_arguments)]
    pub fn iterate_with_sa(
        &mut self,
        max_iterations: usize,
        protect: Option<&[bool]>,
        score_thresh: f32,
        rng: &mut StdRng,
        listeners: &mut [Box<dyn SolverListener>],
        sa_temperature: Option<f32>,
        sa_cooling: f32,
    ) {
        let mut map = match self.map.take() {
            Some(m) => m,
            None => return,
        };

        self.score = self.problem.compute_score(&map);
        if self.score > score_thresh {
            self.report_solution(&map, self.score, listeners);
        }

        let pre = self.problem.precomputed();
        let mut temp = sa_temperature.unwrap_or(0.0);

        let mut iters = 0;
        while iters < max_iterations {
            if self.stop_flag.load(Ordering::Relaxed) {
                break;
            }
            let mut kept_swap = false;
            Self::shuffle(rng, &mut self.swaps);

            for &swap in &self.swaps {
                let a = (swap >> 16) as u8;
                let b = (swap & 0xFFFF) as u8;
                let ma = map[a as usize];
                let mb = map[b as usize];

                if !self
                    .problem
                    .puzzle
                    .initial_map
                    .is_mapping_ok(a, mb as u8)
                    || !self
                        .problem
                        .puzzle
                        .initial_map
                        .is_mapping_ok(b, ma as u8)
                    || protect.is_some_and(|p| p[a as usize] || p[b as usize])
                {
                    continue;
                }

                self.affected.clear();
                for &pos in &pre.pos_by_cipher[a as usize] {
                    if !self.affected_bm[pos] {
                        self.affected_bm[pos] = true;
                        self.affected.push(pos);
                    }
                }
                for &pos in &pre.pos_by_cipher[b as usize] {
                    if !self.affected_bm[pos] {
                        self.affected_bm[pos] = true;
                        self.affected.push(pos);
                    }
                }

                let delta = self
                    .problem
                    .compute_score_delta(&map, a as usize, b as usize, &self.affected);

                for &pos in &self.affected {
                    self.affected_bm[pos] = false;
                }

                let new_score = self.score + delta;

                if new_score > self.score {
                    map[a as usize] = mb;
                    map[b as usize] = ma;
                    self.score = new_score;
                    kept_swap = true;
                    if self.score > score_thresh {
                        self.report_solution(&map, self.score, listeners);
                    }
                } else if sa_temperature.is_some() && temp > 0.0 {
                    let accept_prob = ((new_score - self.score) / temp).exp();
                    if rng.gen::<f32>() < accept_prob {
                        map[a as usize] = mb;
                        map[b as usize] = ma;
                        self.score = new_score;
                        kept_swap = true;
                    }
                }
            }

            if !kept_swap {
                break;
            }
            temp *= sa_cooling;
            iters += 1;
        }

        self.map = Some(map);
    }
}

pub fn find_spaces4(puzzle: &Puzzle, cipher_bytes: &[u8], map: &[i32]) -> Vec<i32> {
    let len = cipher_bytes.len();
    if len < 4 {
        return vec![0i32; len];
    }

    let mut cur = vec![0i32; 8 * len];
    let mut nxt = vec![0i32; 8 * len];
    let mut scores = [0.0f64; 8];

    let grams = puzzle.lang.tetragrams();

    let mut tsv = [0i32; 8];
    for (j, score) in scores.iter_mut().enumerate() {
        let mut tsvlen = 0usize;
        tsv[tsvlen] = map[cipher_bytes[len - 1] as usize];
        tsvlen += 1;
        tsv[tsvlen] = 26;
        tsvlen += 1;
        tsv[tsvlen] = map[cipher_bytes[0] as usize];
        tsvlen += 1;
        if (j & 4) > 0 {
            tsv[tsvlen] = 26;
            tsvlen += 1;
        }
        tsv[tsvlen] = map[cipher_bytes[1] as usize];
        tsvlen += 1;
        if (j & 2) > 0 {
            tsv[tsvlen] = 26;
            tsvlen += 1;
        }
        tsv[tsvlen] = map[cipher_bytes[2] as usize];
        tsvlen += 1;
        if (j & 2) > 0 {
            tsv[tsvlen] = 26;
            tsvlen += 1;
        }
        for k in 0..tsvlen - 3 {
            let ma = tsv[k] as u8;
            let mb = tsv[k + 1] as u8;
            let mc = tsv[k + 2] as u8;
            let md = tsv[k + 3] as u8;
            *score += grams.get_log_prob(ma, mb, mc, md);
        }
    }

    let mut expandedscores = [0.0f64; 16];

    for i in 3..len {
        for j in 0..8 {
            let mut tsvlen = 0usize;
            let mut tsv2 = [0i32; 8];
            tsv2[tsvlen] = map[cipher_bytes[i - 3] as usize];
            tsvlen += 1;
            if (j & 4) > 0 {
                tsv2[tsvlen] = 26;
                tsvlen += 1;
            }
            tsv2[tsvlen] = map[cipher_bytes[i - 2] as usize];
            tsvlen += 1;
            if (j & 2) > 0 {
                tsv2[tsvlen] = 26;
                tsvlen += 1;
            }
            tsv2[tsvlen] = map[cipher_bytes[i - 1] as usize];
            tsvlen += 1;
            if (j & 1) > 0 {
                tsv2[tsvlen] = 26;
                tsvlen += 1;
            }
            tsv2[tsvlen] = map[cipher_bytes[i] as usize];
            tsvlen += 1;

            let md = tsv2[tsvlen - 1] as u8;
            let mc = tsv2[tsvlen - 2] as u8;
            let mb = tsv2[tsvlen - 3] as u8;
            let ma = tsv2[tsvlen - 4] as u8;

            expandedscores[j * 2] = scores[j] + grams.get_log_prob(ma, mb, mc, md);
            if i == len - 1 {
                expandedscores[j * 2] += grams.get_log_prob(mb, mc, md, 26);
            }
            expandedscores[j * 2 + 1] =
                scores[j] + grams.get_log_prob(ma, mb, mc, md) + grams.get_log_prob(mb, mc, md, 26);
        }

        for j in 0..8 {
            let base_j = j * len;
            let base_half = (j / 2) * len;
            if expandedscores[j] >= expandedscores[j + 8] {
                nxt[base_j..base_j + i - 3].copy_from_slice(&cur[base_half..base_half + i - 3]);
                nxt[base_j + i - 3] = 0;
                scores[j] = expandedscores[j];
            } else {
                let base_other = ((j + 8) / 2) * len;
                nxt[base_j..base_j + i - 3].copy_from_slice(&cur[base_other..base_other + i - 3]);
                nxt[base_j + i - 3] = 1;
                scores[j] = expandedscores[j + 8];
            }
        }
        std::mem::swap(&mut cur, &mut nxt);
    }

    let mut best_idx = 0;
    let mut best_score = f64::NEG_INFINITY;
    for (j, &score) in scores.iter().enumerate() {
        let base = j * len;
        cur[base + len - 3] = if (j & 4) > 0 { 1 } else { 0 };
        cur[base + len - 2] = if (j & 2) > 0 { 1 } else { 0 };
        cur[base + len - 1] = if (j & 1) > 0 { 1 } else { 0 };
        if score > best_score {
            best_score = score;
            best_idx = j;
        }
    }
    let start = best_idx * len;
    cur[start + len - 1] = 0;
    cur[start..start + len].to_vec()
}

#[cfg(test)]
mod search_tests {
    use super::*;
    use crate::dict::Dict;
    use rand::SeedableRng;

    fn make_test_puzzle() -> Puzzle {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        Puzzle::new(dict, "ABC DEF GHI", "", false, true)
    }

    #[test]
    fn test_find_spaces4_identity_map() {
        let puzzle = make_test_puzzle();
        let n = puzzle.lang.letter_count();
        let mut map = vec![0i32; n + 1];
        for i in 0..n {
            map[i] = i as i32;
        }
        map[n] = n as i32;
        let spaces = find_spaces4(&puzzle, &puzzle.cipher_bytes, &map);
        assert_eq!(spaces.len(), puzzle.cipher_bytes.len());
    }

    #[test]
    fn test_find_spaces4_short_text() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "AB", "", false, true);
        let n = puzzle.lang.letter_count();
        let mut map = vec![0i32; n + 1];
        for i in 0..n {
            map[i] = i as i32;
        }
        map[n] = n as i32;
        let spaces = find_spaces4(&puzzle, &puzzle.cipher_bytes, &map);
        assert_eq!(spaces.len(), 2);
    }

    #[test]
    fn test_make_swaps_count() {
        let n = 26;
        let swaps = GeneticSearch::make_swaps(n);
        assert_eq!(swaps.len(), n * (n - 1));
    }

    #[test]
    fn test_shuffle_changes_order() {
        let mut rng = StdRng::seed_from_u64(42);
        let mut v = vec![0, 1, 2, 3, 4, 5];
        GeneticSearch::shuffle(&mut rng, &mut v);
        let mut any_diff = false;
        for i in 0..v.len() {
            if v[i] != i {
                any_diff = true;
                break;
            }
        }
        assert!(any_diff);
    }
}
