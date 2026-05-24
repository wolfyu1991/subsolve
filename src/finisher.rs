use rand::Rng;

use crate::mapset::MapSet;
use crate::puzzle::Puzzle;

pub struct Finisher<'a> {
    puzzle: &'a Puzzle,
    start_map: Vec<i32>,
    ourmap: Vec<i32>,
    cletters: Vec<u8>,
    pletters: Vec<u8>,
}

impl<'a> Finisher<'a> {
    pub fn new(puzzle: &'a Puzzle, map: &MapSet) -> Self {
        let letter_count = puzzle.lang.letter_count();
        let mut _cletters = vec![0u8; letter_count];
        let mut ncletters = 0usize;
        let mut _pletters = vec![0u8; letter_count];
        let mut npletters = 0usize;
        let mut mapped = vec![false; letter_count];

        for i in 0..letter_count {
            match map.get_mapping(i as u8) {
                Some(m) => {
                    mapped[m as usize] = true;
                }
                None => {
                    if puzzle.histogram[i] > 0 {
                        _cletters[ncletters] = i as u8;
                        ncletters += 1;
                    }
                }
            }
        }

        for (i, &m) in mapped.iter().enumerate().take(letter_count) {
            if !m {
                _pletters[npletters] = i as u8;
                npletters += 1;
            }
        }

        _cletters.truncate(ncletters);
        _pletters.truncate(npletters);

        let start_map = map.make_map();
        let ourmap = start_map.clone();

        Finisher {
            puzzle,
            start_map,
            ourmap,
            cletters: _cletters,
            pletters: _pletters,
        }
    }

    pub fn brute_force(&mut self) -> MapSet {
        if self.cletters.is_empty() {
            return MapSet::new_from_map(&self.start_map);
        }

        self.ourmap = self.start_map.clone();

        let npletters = self.pletters.len();
        let ncletters = self.cletters.len();
        let iters = npletters.saturating_pow(ncletters as u32);

        if iters > 30000 {
            return MapSet::new_from_map(&self.ourmap);
        }

        let mut newmap = vec![0usize; ncletters];
        let mut bestmap = vec![0i32; self.ourmap.len()];
        let mut bestscore = f64::NEG_INFINITY;

        let mut extended_map = [0i32; 28];
        extended_map[..26].copy_from_slice(&self.ourmap[..26]);

        for iter in 0..iters {
            let mut tmp = iter;
            let mut good = true;

            for i in 0..ncletters {
                newmap[i] = tmp % npletters;
                tmp /= npletters;

                let mut dup = false;
                for j in 0..i {
                    if newmap[j] == newmap[i] {
                        dup = true;
                        break;
                    }
                }
                if dup {
                    good = false;
                    break;
                }

                let c = self.cletters[i];
                let p = self.pletters[newmap[i]];
                if !self.puzzle.initial_map.is_mapping_ok(c, p) {
                    good = false;
                    break;
                }

                extended_map[c as usize] = p as i32;
            }

            if !good {
                continue;
            }

            extended_map[26] = 26;
            extended_map[27] = 27;

            let score = self
                .puzzle
                .lang
                .compute_score_map(&self.puzzle.cipher_bytes, &extended_map);
            if score > bestscore {
                bestscore = score;
                bestmap[..26].copy_from_slice(&extended_map[..26]);
            }

            extended_map[26] = 0;
            extended_map[27] = 0;
        }

        MapSet::new_from_map(&bestmap)
    }

    pub fn randomize(&mut self) {
        let mut rng = rand::thread_rng();
        for i in 0..self.cletters.len() {
            let j = loop {
                let j = rng.gen_range(0..self.pletters.len());
                let mut ok = true;
                for k in 0..i {
                    if self.ourmap[self.cletters[k] as usize] == self.pletters[j] as i32 {
                        ok = false;
                        break;
                    }
                }
                if ok {
                    break j;
                }
            };
            self.ourmap[self.cletters[i] as usize] = self.pletters[j] as i32;
        }
    }

}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::dict::Dict;
    use crate::language::EnglishLanguage;
    use crate::language::Language;

    #[test]
    fn test_finisher_empty_cletters() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, true);

        let mut map = MapSet::new(26);
        map.set_full_set();
        for i in 0..26 {
            map.set_mapping(i as u8, i as u8);
        }

        let mut finisher = Finisher::new(&puzzle, &map);
        let result = finisher.brute_force();

        for i in 0..26 {
            assert_eq!(result.get_mapping(i as u8), Some(i as u8));
        }
    }

    #[test]
    fn test_finisher_too_many_iters() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, true);

        let mut map = MapSet::new(26);
        map.set_full_set();
        map.set_mapping(7, 7);

        let mut finisher = Finisher::new(&puzzle, &map);
        let result = finisher.brute_force();

        assert_eq!(result.get_mapping(7), Some(7));
    }

    #[test]
    fn test_finisher_brute_force_small() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, true);

        let mut map = MapSet::new(26);
        map.set_full_set();
        for i in 0..26 {
            if i != 11 && i != 14 {
                map.set_mapping(i as u8, i as u8);
            }
        }

        let mut finisher = Finisher::new(&puzzle, &map);

        assert_eq!(finisher.cletters.len(), 2);
        assert_eq!(finisher.pletters.len(), 2);

        let result = finisher.brute_force();

        for i in 0..26 {
            assert!(result.is_uniquely_mapped(i as u8), "letter {} not uniquely mapped", i);
        }
        assert!(result.get_mapping(11).is_some());
        assert!(result.get_mapping(14).is_some());
    }

    #[test]
    fn test_finisher_constructor_filters_by_histogram() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, true);

        let mut map = MapSet::new(26);
        map.set_full_set();

        let finisher = Finisher::new(&puzzle, &map);

        let expected_cletters: Vec<u8> = vec![4, 7, 11, 14];
        assert_eq!(finisher.cletters.len(), expected_cletters.len());
        for &c in &expected_cletters {
            assert!(finisher.cletters.contains(&c), "missing cipher letter {}", c);
        }
    }

    #[test]
    fn test_finisher_randomize() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HI", "", true, true);

        let mut map = MapSet::new(26);
        map.set_full_set();
        for i in 0..26 {
            if i != 7 && i != 8 {
                map.set_mapping(i as u8, i as u8);
            }
        }

        let mut finisher = Finisher::new(&puzzle, &map);
        finisher.brute_force();
        finisher.randomize();

        assert!(finisher.ourmap[7] >= 0);
        assert!(finisher.ourmap[8] >= 0);
        assert_ne!(finisher.ourmap[7], finisher.ourmap[8]);
    }

    #[test]
    fn test_finisher_identity_mapping_high_score() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, true);

        let mut map = MapSet::new(26);
        map.set_full_set();
        for i in 0..26 {
            if i != 11 && i != 14 {
                map.set_mapping(i as u8, i as u8);
            }
        }

        let mut finisher = Finisher::new(&puzzle, &map);
        let result = finisher.brute_force();

        let lang = EnglishLanguage::new();

        let mut ident_map = [0i32; 28];
        for i in 0..26 { ident_map[i] = i as i32; }
        ident_map[26] = 26;
        ident_map[27] = 27;
        let identity_score = lang.compute_score_map(&puzzle.cipher_bytes, &ident_map);

        let mut result_ext = [0i32; 28];
        let result_raw = result.make_map();
        for i in 0..26 { result_ext[i] = result_raw[i]; }
        result_ext[26] = 26;
        result_ext[27] = 27;
        let result_score = lang.compute_score_map(&puzzle.cipher_bytes, &result_ext);

        assert!((result_score - identity_score).abs() < 0.001,
            "result score {:.6} should match identity score {:.6}", result_score, identity_score);
    }

}
