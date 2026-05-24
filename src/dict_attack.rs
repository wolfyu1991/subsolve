use crate::language::Language;
use crate::mapset::MapSet;
pub(crate) use crate::params::DecryptoParameters;
pub(crate) use crate::planner::{planner_words_from, LazyPlanner, Planner, RandomPlanner};
#[cfg(test)]
pub(crate) use crate::planner::{LETTER_FLAG, MASK, WORD_FLAG};
use crate::puzzle::Puzzle;
pub use crate::simple_dict_attack::SimpleDictionaryAttack;
use crate::solver::{Solver, SolverListener};
use crate::solver::StopFlag;
use crate::word::Word;

pub struct DictionaryAttackSolver {
    pub puzzle: Puzzle,
    pub listeners: Vec<Box<dyn SolverListener>>,
    pub stop_flag: Option<StopFlag>,
    pub end_time: Option<f64>,
    pub params: DecryptoParameters,
}

impl DictionaryAttackSolver {
    pub fn new(puzzle: Puzzle) -> Self {
        DictionaryAttackSolver {
            puzzle,
            listeners: Vec::new(),
            stop_flag: None,
            end_time: None,
            params: DecryptoParameters::default(),
        }
    }

    pub fn set_params(&mut self, params: DecryptoParameters) {
        self.params = params;
    }

    #[allow(clippy::too_many_arguments)]
    fn make_sda<'a>(
        words: &'a mut [Word],
        cipher_bytes: &'a [u8],
        lang: &'a dyn Language,
        initial_map: &'a MapSet,
        listeners: &'a mut Vec<Box<dyn SolverListener>>,
        num_ciphertext_letters: usize,
        planner: Box<dyn Planner>,
        prune_root_node: bool,
        prune_each_node: bool,
        scramble_word_order: bool,
        max_iters: usize,
    ) -> SimpleDictionaryAttack<'a> {
        let n_words = words.len();
        let letter_count = lang.letter_count();
        SimpleDictionaryAttack {
            words,
            num_ciphertext_letters,
            cipher_bytes,
            lang,
            initial_map,
            planner,
            solved_words: vec![false; n_words],
            num_solved_words: 0,
            got_full_solution: false,
            listeners,
            stop_flag: None,
            prune_root_node,
            prune_each_node,
            scramble_word_order,
            iters: 0,
            max_iters,
            reportdepth: 2,
            worksize: [0; 10],
            workprogress: [0; 10],
            lastprogress: -1.0,
            tmp_set: MapSet::new(letter_count),
            firstcand_buf: Vec::with_capacity(n_words),
        }
    }
}

impl Solver for DictionaryAttackSolver {
    fn add_listener(&mut self, listener: Box<dyn SolverListener>) {
        self.listeners.push(listener);
    }

    fn start(&mut self, max_time: f64) {
        let stop_flag = StopFlag::new(max_time);
        self.stop_flag = Some(stop_flag);
        self.end_time = None;

        for w in self.puzzle.words.iter_mut() {
            w.enabled = true;
        }

        for listener in self.listeners.iter_mut() {
            listener.on_message("Doing initial search...");
        }

        let lazy_planner = LazyPlanner::new(
            planner_words_from(&self.puzzle.words),
            self.puzzle.historder.clone(),
            self.puzzle.histogram.clone(),
            self.puzzle.lang.letter_count(),
            &self.params,
        );

        let mut sda = Self::make_sda(
            &mut self.puzzle.words,
            &self.puzzle.cipher_bytes,
            &*self.puzzle.lang,
            &self.puzzle.initial_map,
            &mut self.listeners,
            self.puzzle.num_ciphertext_letters,
            Box::new(lazy_planner),
            true, true, false, usize::MAX,
        );

        sda.solve(self.stop_flag.as_ref().unwrap());

        if !sda.got_full_solution {
            let num_words = self.puzzle.words.len();
            let max_random_disable = self.params.max_random_word_disable;
            let max_random_iters_val = self.params.max_random_word_iterations;

            let mut max_disable_word_count = max_random_disable.min(num_words.saturating_sub(1));
            while max_disable_word_count > 1
                && (num_words as f64).powi(max_disable_word_count as i32) > max_random_iters_val as f64
            {
                max_disable_word_count -= 1;
            }

            let mut got_solution = false;
            let mut iters = 1usize;
            for disable_word_count in 1..=max_disable_word_count {
                iters = iters.saturating_mul(num_words);
                if self.stop_flag.as_ref().unwrap().should_stop() {
                    break;
                }
                for i in 0..=iters {
                    for w in self.puzzle.words.iter_mut() {
                        w.enabled = true;
                    }

                    let mut tmp = i;
                    let mut bad = false;
                    let mut msg = "Disabling words: ".to_string();
                    for _ in 0..disable_word_count {
                        let widx = tmp % num_words;
                        tmp /= num_words;
                        if !self.puzzle.words[widx].enabled {
                            bad = true;
                        }
                        self.puzzle.words[widx].enabled = false;
                        msg.push_str(&self.puzzle.words[widx].text);
                        msg.push(' ');
                    }
                    if bad {
                        continue;
                    }

                    for listener in self.listeners.iter_mut() {
                        listener.on_message(&msg);
                    }

                    let lazy_planner2 = LazyPlanner::new(
                        planner_words_from(&self.puzzle.words),
                        self.puzzle.historder.clone(),
                        self.puzzle.histogram.clone(),
                        self.puzzle.lang.letter_count(),
                        &self.params,
                    );

                    let mut sda2 = Self::make_sda(
                        &mut self.puzzle.words,
                        &self.puzzle.cipher_bytes,
                        &*self.puzzle.lang,
                        &self.puzzle.initial_map,
                        &mut self.listeners,
                        self.puzzle.num_ciphertext_letters,
                        Box::new(lazy_planner2),
                        true, true, false, usize::MAX,
                    );

                    sda2.solve(self.stop_flag.as_ref().unwrap());
                    got_solution |= sda2.got_full_solution;
                    if got_solution {
                        break;
                    }
                }
                if got_solution {
                    break;
                }
            }

            for listener in self.listeners.iter_mut() {
                listener.on_message("Switching to randomized search...");
            }

            let mut max_iters = 5000usize;
            while !self.stop_flag.as_ref().unwrap().should_stop() {
                let random_planner = RandomPlanner::new(
                    self.puzzle.words.iter().map(|w| w.enabled).collect(),
                );

                let mut sda3 = Self::make_sda(
                    &mut self.puzzle.words,
                    &self.puzzle.cipher_bytes,
                    &*self.puzzle.lang,
                    &self.puzzle.initial_map,
                    &mut self.listeners,
                    self.puzzle.num_ciphertext_letters,
                    Box::new(random_planner),
                    false, false, true, max_iters,
                );

                sda3.solve(self.stop_flag.as_ref().unwrap());
                max_iters = ((max_iters as f64) * 1.05) as usize;
            }
        }

        let elapsed = self.stop_flag.as_ref().unwrap().elapsed();
        self.end_time = Some(elapsed);

        for listener in self.listeners.iter_mut() {
            listener.on_finished(elapsed);
        }

        if let Some(ref sf) = self.stop_flag {
            sf.stop.store(true, std::sync::atomic::Ordering::Relaxed);
        }
    }

    fn stop(&mut self) {
        if let Some(ref sf) = self.stop_flag {
            sf.stop.store(true, std::sync::atomic::Ordering::Relaxed);
        }
        if self.end_time.is_none() {
            self.end_time = Some(self.elapsed_time());
        }
    }

    fn elapsed_time(&self) -> f64 {
        self.stop_flag.as_ref().map_or(0.0, |sf| sf.elapsed())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::word::Candidate;
    use crate::dict::Dict;
    use crate::language::EnglishLanguage;
    use std::sync::{Arc, Mutex};
    use crate::mapset::MapSet;
    use super::LazyPlanner;
    use crate::puzzle::Puzzle;

    struct TestListenerData {
        pub messages: Vec<String>,
        pub solutions: Vec<(bool, f64)>,
        pub finished: bool,
        pub progress: f64,
    }

    struct TestListener {
        pub data: Arc<Mutex<TestListenerData>>,
    }

    impl TestListener {
        fn new() -> Self {
            TestListener {
                data: Arc::new(Mutex::new(TestListenerData {
                    messages: Vec::new(),
                    solutions: Vec::new(),
                    finished: false,
                    progress: 0.0,
                })),
            }
        }
    }

    impl SolverListener for TestListener {
        fn on_message(&mut self, msg: &str) {
            if let Ok(mut d) = self.data.lock() {
                d.messages.push(msg.to_string());
            }
        }

        fn on_progress(&mut self, progress: f64) {
            if let Ok(mut d) = self.data.lock() {
                d.progress = progress;
            }
        }

        fn on_finished(&mut self, _elapsed: f64) {
            if let Ok(mut d) = self.data.lock() {
                d.finished = true;
            }
        }

        fn on_solution(&mut self, _mapset: &MapSet, _spaces: Option<Vec<i32>>, full_solution: bool, score: f64) {
            if let Ok(mut d) = self.data.lock() {
                d.solutions.push((full_solution, score));
            }
        }
    }

    #[test]
    fn test_round_score() {
        let result = SimpleDictionaryAttack::round_score(1.23456789);
        assert!((result - 1.234565).abs() < 0.0001);
    }

    #[test]
    fn test_save_restore_firstcand() {
        let mut words = vec![
            Word::new("HELLO".to_string(), b"HELLO".to_vec()),
            Word::new("WORLD".to_string(), b"WORLD".to_vec()),
        ];
        words[0].firstcand = 3;
        words[1].firstcand = 5;

        let ms = MapSet::new(26);
        let lang = EnglishLanguage::new();
        let params = DecryptoParameters::default();

        let planner = LazyPlanner::new(
            planner_words_from(&words),
            vec![],
            vec![0; 26],
            26,
            &params,
        );

        let mut sda = SimpleDictionaryAttack {
            words: &mut words,
            num_ciphertext_letters: 10,
            cipher_bytes: &[],
            lang: &lang,
            initial_map: &ms,
            planner: Box::new(planner),
            solved_words: vec![false, false],
            num_solved_words: 0,
            got_full_solution: false,
            listeners: &mut vec![],
            stop_flag: None,
            prune_root_node: false,
            prune_each_node: false,
            scramble_word_order: false,
            iters: 0,
            max_iters: 100,
            reportdepth: 2,
            worksize: [0; 10],
            workprogress: [0; 10],
            lastprogress: -1.0,
            tmp_set: MapSet::new(26),
            firstcand_buf: Vec::new(),
        };

        let saved_len = sda.save_firstcand_state();
        assert_eq!(sda.firstcand_buf, vec![3, 5]);

        sda.words[0].firstcand = 10;
        sda.words[1].firstcand = 20;

        sda.restore_firstcand_state(saved_len);
        assert_eq!(sda.words[0].firstcand, 3);
        assert_eq!(sda.words[1].firstcand, 5);
    }

    #[test]
    fn test_reduce_candidates_no_change() {
        let mut words = vec![
            Word::new("HELLO".to_string(), vec![7, 4, 11, 11, 14]),
        ];
        words[0].candidates = Some(vec![
            Candidate { cand: vec![7, 4, 11, 11, 14], score: 1.0 },
        ]);

        let mut ms = MapSet::new(26);
        ms.set_full_set();
        let lang = EnglishLanguage::new();
        let params = DecryptoParameters::default();

        let planner = LazyPlanner::new(
            planner_words_from(&words),
            vec![],
            vec![0; 26],
            26,
            &params,
        );

        let mut sda = SimpleDictionaryAttack {
            words: &mut words,
            num_ciphertext_letters: 5,
            cipher_bytes: &[],
            lang: &lang,
            initial_map: &ms,
            planner: Box::new(planner),
            solved_words: vec![false],
            num_solved_words: 0,
            got_full_solution: false,
            listeners: &mut vec![],
            stop_flag: None,
            prune_root_node: false,
            prune_each_node: false,
            scramble_word_order: false,
            iters: 0,
            max_iters: 100,
            reportdepth: 2,
            worksize: [0; 10],
            workprogress: [0; 10],
            lastprogress: -1.0,
            tmp_set: MapSet::new(26),
            firstcand_buf: Vec::new(),
        };

        let mut test_map = MapSet::new(26);
        test_map.set_full_set();
        let inconsistent = sda.reduce_candidates(&mut test_map);
        assert!(!inconsistent);
    }

    #[test]
    fn test_dictionary_attack_solver_hello_world() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO WORLD", "", true, true);
        let mut solver = DictionaryAttackSolver::new(puzzle);

        let listener = TestListener::new();
        let data = Arc::clone(&listener.data);
        solver.add_listener(Box::new(listener));

        solver.start(2.0);
        let d = data.lock().unwrap();
        assert!(d.finished, "solver should finish within timeout");
        assert!(!d.solutions.is_empty(), "solver should find at least one solution");
        let best_score = d.solutions.iter().map(|&(_, s)| s).fold(f64::NEG_INFINITY, f64::max);
        assert!(!best_score.is_nan(), "best solution score should not be NaN");
    }

    #[test]
    fn test_lazy_planner_selects_smallest_candidate_count() {
        let mut words = make_mock_words();
        words[0].candidates = Some(vec![
            crate::word::Candidate { cand: vec![7, 4, 11, 11, 14], score: 1.0 },
        ]);
        words[1].candidates = Some(vec![
            crate::word::Candidate { cand: vec![22, 14, 17, 11, 3], score: 1.0 },
            crate::word::Candidate { cand: vec![22, 14, 17, 11, 3], score: 0.5 },
        ]);
        words[2].candidates = Some(vec![
            crate::word::Candidate { cand: vec![19, 4, 18, 19], score: 1.0 },
            crate::word::Candidate { cand: vec![19, 4, 18, 19], score: 0.5 },
            crate::word::Candidate { cand: vec![19, 4, 18, 19], score: 0.1 },
        ]);

        let params = DecryptoParameters::default();
        let mut planner = LazyPlanner::new(
            planner_words_from(&words),
            vec![7, 4, 11, 14, 22, 17, 3, 19, 18],
            vec![0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 2, 0, 0, 2, 0, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0],
            26,
            &params,
        );

        let solved = vec![false, false, false];
        let map = MapSet::new(26);
        let action = planner.get_action(&solved, &map);
        assert_eq!(action & MASK, 0);
        assert_eq!(action & WORD_FLAG, WORD_FLAG);
    }

    #[test]
    fn test_lazy_planner_returns_neg2_when_zero_candidates() {
        let mut words = make_mock_words();
        words[0].candidates = Some(vec![]);
        words[0].firstcand = 0;

        let params = DecryptoParameters::default();
        let mut planner = LazyPlanner::new(
            planner_words_from(&words),
            vec![],
            vec![0; 26],
            26,
            &params,
        );

        let solved = vec![false];
        let map = MapSet::new(26);
        assert_eq!(planner.get_action(&solved, &map), -2);
    }

    #[test]
    fn test_lazy_planner_skips_solved_words() {
        let mut words = make_mock_words();
        words[0].candidates = Some(vec![
            crate::word::Candidate { cand: vec![7, 4, 11, 11, 14], score: 1.0 },
        ]);
        words[1].candidates = Some(vec![
            crate::word::Candidate { cand: vec![22, 14, 17, 11, 3], score: 1.0 },
            crate::word::Candidate { cand: vec![22, 14, 17, 11, 3], score: 0.5 },
        ]);

        let params = DecryptoParameters::default();
        let mut planner = LazyPlanner::new(
            planner_words_from(&words),
            vec![],
            vec![0; 26],
            26,
            &params,
        );

        let solved = vec![true, false];
        let map = MapSet::new(26);
        let action = planner.get_action(&solved, &map);
        assert_eq!(action & MASK, 1);
    }

    #[test]
    fn test_lazy_planner_skips_disabled_words() {
        let mut words = make_mock_words();
        words[0].candidates = Some(vec![
            crate::word::Candidate { cand: vec![7, 4, 11, 11, 14], score: 1.0 },
        ]);
        words[1].candidates = Some(vec![
            crate::word::Candidate { cand: vec![22, 14, 17, 11, 3], score: 1.0 },
            crate::word::Candidate { cand: vec![22, 14, 17, 11, 3], score: 0.5 },
        ]);
        words[1].enabled = false;

        let params = DecryptoParameters::default();
        let mut planner = LazyPlanner::new(
            planner_words_from(&words),
            vec![],
            vec![0; 26],
            26,
            &params,
        );

        let solved = vec![false, false];
        let map = MapSet::new(26);
        let action = planner.get_action(&solved, &map);
        assert_eq!(action & MASK, 0);
    }

    #[test]
    fn test_lazy_planner_letter_flag() {
        let mut words = make_mock_words();
        words[0].candidates = Some(vec![
            crate::word::Candidate { cand: vec![7, 4, 11, 11, 14], score: 1.0 },
            crate::word::Candidate { cand: vec![7, 4, 11, 11, 14], score: 0.5 },
        ]);

        let mut params = DecryptoParameters::default();
        params.enable_single_letters = true;

        let mut planner = LazyPlanner::new(
            planner_words_from(&words),
            vec![7, 4, 11, 14],
            vec![0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 2, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
            26,
            &params,
        );

        let solved = vec![false];
        let map = MapSet::new(26);
        let action = planner.get_action(&solved, &map);
        assert_eq!(action & WORD_FLAG, WORD_FLAG);
        assert_eq!(action & MASK, 0);
    }

    #[test]
    fn test_lazy_planner_letter_flag_triggers_when_score_high() {
        let mut words = make_mock_words();
        words[0].candidates = Some(vec![
            crate::word::Candidate { cand: vec![7, 4, 11, 11, 14], score: 1.0 };
            30
        ]);

        let mut params = DecryptoParameters::default();
        params.enable_single_letters = true;

        let historder = vec![7u8, 4, 11, 14];
        let histogram = vec![
            0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 2, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        ];

        let mut planner = LazyPlanner::new(
            planner_words_from(&words),
            historder,
            histogram,
            26,
            &params,
        );

        let solved = vec![false];
        let map = MapSet::new(26);
        let action = planner.get_action(&solved, &map);
        assert_eq!(action & LETTER_FLAG, LETTER_FLAG);
        assert_eq!(action & MASK, 7);
    }

    #[test]
    fn test_lazy_planner_returns_neg2_when_all_solved() {
        let words = make_mock_words();
        let params = DecryptoParameters::default();
        let mut planner = LazyPlanner::new(
            planner_words_from(&words),
            vec![],
            vec![0; 26],
            26,
            &params,
        );

        let solved = vec![true, true, true];
        let map = MapSet::new(26);
        let action = planner.get_action(&solved, &map);
        assert_eq!(action, (-1) | WORD_FLAG);
    }

    #[test]
    fn test_lazy_planner_returns_neg2_when_all_disabled() {
        let mut words = make_mock_words();
        words[0].enabled = false;
        words[1].enabled = false;
        words[2].enabled = false;
        let params = DecryptoParameters::default();
        let mut planner = LazyPlanner::new(
            planner_words_from(&words),
            vec![],
            vec![0; 26],
            26,
            &params,
        );

        let solved = vec![false, false, false];
        let map = MapSet::new(26);
        let action = planner.get_action(&solved, &map);
        assert_eq!(action, (-1) | WORD_FLAG);
    }

    #[test]
    fn test_random_planner_eventually_picks_enabled_word() {
        let words = make_mock_words();
        let mut planner = RandomPlanner::new(
            words.iter().map(|w| w.enabled).collect(),
        );

        let solved = vec![false, false, false];
        let map = MapSet::new(26);
        let action = planner.get_action(&solved, &map);
        assert_eq!(action & WORD_FLAG, WORD_FLAG);
        let widx = action & MASK;
        assert!(widx >= 0 && widx < 3);
    }

    #[test]
    fn test_random_planner_returns_neg1_after_many_trials() {
        let words = make_mock_words();
        let mut planner = RandomPlanner::new(
            words.iter().map(|w| w.enabled).collect(),
        );

        let solved = vec![true, true, true];
        let map = MapSet::new(26);
        assert_eq!(planner.get_action(&solved, &map), -1);
    }

    #[test]
    fn test_random_planner_returns_neg1_when_all_disabled() {
        let mut words = make_mock_words();
        words[0].enabled = false;
        words[1].enabled = false;
        words[2].enabled = false;
        let mut planner = RandomPlanner::new(
            words.iter().map(|w| w.enabled).collect(),
        );

        let solved = vec![false, false, false];
        let map = MapSet::new(26);
        assert_eq!(planner.get_action(&solved, &map), -1);
    }

    #[test]
    fn test_planner_constants() {
        assert_eq!(WORD_FLAG, 0x100000);
        assert_eq!(LETTER_FLAG, 0x200000);
        assert_eq!(MASK, 65535);
    }
}

#[cfg(test)]
fn make_mock_words() -> Vec<Word> {
    vec![
        Word::new("HELLO".to_string(), vec![7, 4, 11, 11, 14]),
        Word::new("WORLD".to_string(), vec![22, 14, 17, 11, 3]),
        Word::new("TEST".to_string(), vec![19, 4, 18, 19]),
    ]
}
