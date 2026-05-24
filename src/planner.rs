use rand::Rng;

use crate::mapset::MapSet;
use crate::params::DecryptoParameters;
use crate::word::Word;

pub const WORD_FLAG: i32 = 0x100000;
pub const LETTER_FLAG: i32 = 0x200000;
pub const MASK: i32 = 65535;

pub trait Planner: Send {
    fn get_action(&mut self, solved_words: &[bool], map: &MapSet) -> i32;
}

pub(crate) struct PlannerWord {
    pub enabled: bool,
    pub firstcand: usize,
    pub candidates_len: usize,
}

pub(crate) fn planner_words_from(words: &[Word]) -> Vec<PlannerWord> {
    words.iter().map(|w| PlannerWord {
        enabled: w.enabled,
        firstcand: w.firstcand,
        candidates_len: w.candidates.as_ref().map(|c| c.len()).unwrap_or(0),
    }).collect()
}

pub struct LazyPlanner {
    words: Vec<PlannerWord>,
    historder: Vec<u8>,
    histogram: Vec<i32>,
    letter_count: usize,
    enable_single_letters: bool,
}

impl LazyPlanner {
    pub(crate) fn new(
        words: Vec<PlannerWord>,
        historder: Vec<u8>,
        histogram: Vec<i32>,
        letter_count: usize,
        params: &DecryptoParameters,
    ) -> Self {
        Self {
            words,
            historder,
            histogram,
            letter_count,
            enable_single_letters: params.enable_single_letters,
        }
    }
}

impl Planner for LazyPlanner {
    fn get_action(&mut self, solved_words: &[bool], map: &MapSet) -> i32 {
        let mut bestscore = f64::MAX;
        let mut bestword: i32 = -1;

        for (i, &solved) in solved_words.iter().enumerate() {
            if solved {
                continue;
            }
            let w = &self.words[i];
            if !w.enabled {
                continue;
            }
            let thisscore = w.candidates_len as f64 - w.firstcand as f64;
            if thisscore == 0.0 {
                return -2;
            }
            if thisscore < bestscore {
                bestscore = thisscore;
                bestword = i as i32;
            }
        }

        if self.enable_single_letters && bestscore > self.letter_count as f64 && bestword != -1 {
            for &letter in &self.historder {
                if map.is_uniquely_mapped(letter) || self.histogram[letter as usize] == 0 {
                    continue;
                }
                return (letter as i32) | LETTER_FLAG;
            }
        }

        bestword | WORD_FLAG
    }
}

pub struct RandomPlanner {
    enabled: Vec<bool>,
}

impl RandomPlanner {
    pub(crate) fn new(enabled: Vec<bool>) -> Self {
        Self { enabled }
    }
}

impl Planner for RandomPlanner {
    fn get_action(&mut self, solved_words: &[bool], _map: &MapSet) -> i32 {
        let mut rng = rand::thread_rng();
        let mut count = 0usize;
        let mut selected = None;
        for (i, (&solved, &enabled)) in solved_words.iter().zip(self.enabled.iter()).enumerate() {
            if solved || !enabled {
                continue;
            }
            count += 1;
            if rng.gen_range(0..count) == 0 {
                selected = Some(i);
            }
        }
        match selected {
            Some(widx) => WORD_FLAG | widx as i32,
            None => -1,
        }
    }
}
