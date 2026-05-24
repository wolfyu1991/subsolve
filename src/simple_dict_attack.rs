use rand::Rng;

use crate::language::Language;
use crate::mapset::MapSet;
pub(crate) use crate::params::DecryptoParameters;
use crate::planner::{Planner, LETTER_FLAG, MASK, WORD_FLAG};
use crate::solver::SolverListener;
use crate::solver::StopFlag;
use crate::word::Word;

pub struct SimpleDictionaryAttack<'a> {
    pub words: &'a mut [Word],
    pub num_ciphertext_letters: usize,
    pub cipher_bytes: &'a [u8],
    pub lang: &'a dyn Language,
    pub initial_map: &'a MapSet,
    pub planner: Box<dyn Planner>,
    pub solved_words: Vec<bool>,
    pub num_solved_words: usize,
    pub got_full_solution: bool,
    pub listeners: &'a mut Vec<Box<dyn SolverListener>>,
    pub stop_flag: Option<&'a StopFlag>,
    pub prune_root_node: bool,
    pub prune_each_node: bool,
    pub scramble_word_order: bool,
    pub iters: usize,
    pub max_iters: usize,
    pub reportdepth: usize,
    pub worksize: [usize; 10],
    pub workprogress: [usize; 10],
    pub lastprogress: f64,
    pub(crate) tmp_set: MapSet,
    pub(crate) firstcand_buf: Vec<usize>,
}

impl<'a> SimpleDictionaryAttack<'a> {
    #[cfg(test)]
    pub fn round_score(in_val: f64) -> f64 {
        let t = 262144.0;
        let s = (in_val * t) as i64;
        s as f64 / t
    }

    pub fn solve(&mut self, stop_flag: &'a StopFlag) {
        self.stop_flag = Some(stop_flag);
        let mut map = self.initial_map.copy_from();
        for w in self.words.iter_mut() {
            w.firstcand = 0;
        }
        self.num_solved_words = 0;
        let inconsistent = if self.prune_root_node {
            self.reduce_candidates(&mut map)
        } else {
            false
        };
        if inconsistent {
            map = self.initial_map.copy_from();
        }
        self.solve_recursive(0, &mut map);
    }

    fn report_solution(&mut self, map: &MapSet, full_solution: bool) {
        if full_solution {
            self.got_full_solution = true;
        }
        let score = self.lang.compute_score_mapset(self.cipher_bytes, map) / self.num_ciphertext_letters as f64;
        for listener in self.listeners.iter_mut() {
            listener.on_solution(map, None, full_solution, score);
        }
    }

    fn solve_recursive(&mut self, depth: usize, map: &mut MapSet) {
        self.iters += 1;
        let stopped = self.stop_flag.is_some_and(|sf| sf.should_stop());
        if stopped || self.iters > self.max_iters {
            return;
        }
        if self.num_solved_words == self.words.len() {
            self.report_solution(map, true);
            return;
        }
        let action = self.planner.get_action(&self.solved_words, map);
        if action == -1 {
            self.report_solution(map, true);
            return;
        }
        if action == -2 {
            self.report_solution(map, false);
            return;
        }

        if action & LETTER_FLAG != 0 {
            let a_byte = (action & MASK) as u8;
            let firstcand_save = self.save_firstcand_state();
            let mut ourmap = map.copy_from();
            self.report_progress_size(depth, self.lang.letter_count());
            let mut leaf = true;
            for c in 0..self.lang.letter_count() {
                let b_byte = c as u8;
                self.report_progress(depth, c);
                if !ourmap.is_mapping_ok(a_byte, b_byte) {
                    continue;
                }
                ourmap.set_mappings(&[a_byte], &[b_byte]);
                let inconsistent = self.reduce_candidates(&mut ourmap);
                if !inconsistent {
                    self.solve_recursive(depth + 1, &mut ourmap);
                    leaf = false;
                }
                ourmap.set_to(map);
                self.restore_firstcand_state(firstcand_save);
            }
            if leaf {
                self.report_solution(map, false);
            }
            return;
        }

        if action & WORD_FLAG != 0 {
            let wordnum = (action & MASK) as usize;
            self.solved_words[wordnum] = true;
            self.num_solved_words += 1;

            if !self.words[wordnum].enabled {
                self.solve_recursive(depth + 1, map);
            } else {
                if self.scramble_word_order {
                    let w = &mut self.words[wordnum];
                    if let Some(ref mut candidates) = w.candidates {
                        let firstc = w.firstcand;
                        let len = candidates.len();
                        let mut rng = rand::thread_rng();
                        for ci in firstc..len {
                            let d = rng.gen_range(firstc..len);
                            candidates.swap(ci, d);
                        }
                    }
                }

                let word_word = self.words[wordnum].word.clone();
                let w_firstcand = self.words[wordnum].firstcand;
                let saved_candidates = self.words[wordnum].candidates.take().unwrap_or_default();

                let firstcand_save = self.save_firstcand_state();
                let mut ourmap = map.copy_from();
                self.report_progress_size(depth, saved_candidates.len().saturating_sub(w_firstcand));

                let mut leaf = true;
                let mut idx = w_firstcand;
                while idx < saved_candidates.len() {
                    self.report_progress(depth, idx - w_firstcand);
                    if !ourmap.is_mappings_ok(&word_word, &saved_candidates[idx].cand) {
                        idx += 1;
                        continue;
                    }
                    leaf = false;
                    ourmap.set_mappings(&word_word, &saved_candidates[idx].cand);
                    let inconsistent = if self.prune_each_node {
                        self.reduce_candidates(&mut ourmap)
                    } else {
                        false
                    };
                    if !inconsistent {
                        self.solve_recursive(depth + 1, &mut ourmap);
                    }
                    ourmap.set_to(map);
                    self.restore_firstcand_state(firstcand_save);
                    idx += 1;
                }

                self.words[wordnum].candidates = Some(saved_candidates);
                if leaf {
                    self.report_solution(map, false);
                }
            }

            self.num_solved_words -= 1;
            self.solved_words[wordnum] = false;
        }
    }

    pub(crate) fn save_firstcand_state(&mut self) -> usize {
        self.firstcand_buf.clear();
        for w in self.words.iter() {
            self.firstcand_buf.push(w.firstcand);
        }
        self.firstcand_buf.len()
    }

    pub(crate) fn restore_firstcand_state(&mut self, saved_len: usize) {
        for (i, &firstcand) in self.firstcand_buf[..saved_len].iter().enumerate() {
            if i < self.words.len() {
                self.words[i].firstcand = firstcand;
            }
        }
    }

    pub(crate) fn reduce_candidates(&mut self, set: &mut MapSet) -> bool {
        self.tmp_set.set_empty_set();
        let mut dirty = true;
        let max_iters = DecryptoParameters::default().max_reduce_iterations;
        for _ in 0..max_iters {
            if !dirty {
                break;
            }
            if self.stop_flag.is_some_and(|sf| sf.should_stop()) {
                break;
            }
            dirty = false;

            for wordnum in 0..self.words.len() {
                if self.solved_words[wordnum] {
                    continue;
                }
                if !self.words[wordnum].enabled {
                    continue;
                }
                self.tmp_set.set_empty_set();
                {
                    let w = &mut self.words[wordnum];
                    let firstc = w.firstcand;
                    if let Some(ref mut candidates) = w.candidates {
                        let mut c = firstc;
                        while c < candidates.len() {
                            if set.is_mappings_ok(&w.word, &candidates[c].cand) {
                                self.tmp_set.enable_mappings(&w.word, &candidates[c].cand);
                            } else {
                                candidates.swap(w.firstcand, c);
                                w.firstcand += 1;
                                dirty = true;
                            }
                            c += 1;
                        }
                    }
                }
                set.intersect_with(&self.tmp_set);
            }

            if dirty {
                set.self_reduce();
            }

            let mut mapped_to = 0u64;
            for i in 0..self.lang.letter_count() {
                if !set.has_mappings(i as u8) {
                    return true;
                }
                if let Some(mi) = set.get_mapping(i as u8) {
                    let bit = 1u64 << mi;
                    if (mapped_to & bit) != 0 {
                        return true;
                    }
                    mapped_to |= bit;
                }
            }
        }
        false
    }

    fn report_progress_size(&mut self, depth: usize, amount: usize) {
        if depth <= self.reportdepth {
            self.worksize[depth] = amount;
        }
    }

    fn report_progress(&mut self, depth: usize, amount: usize) {
        if depth > self.reportdepth {
            return;
        }
        self.workprogress[depth] = amount;
        let mut frac = 1.0;
        let mut prog = 0.0;
        for i in 0..=depth {
            if self.worksize[i] == 0 {
                continue;
            }
            prog += frac * self.workprogress[i] as f64 / self.worksize[i] as f64;
            frac /= self.worksize[i] as f64;
        }
        if prog - self.lastprogress < 0.03 {
            return;
        }
        self.lastprogress = prog;
        for listener in self.listeners.iter_mut() {
            listener.on_progress(prog);
        }
    }
}
