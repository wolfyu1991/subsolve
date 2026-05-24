use std::cmp::Ordering;

use crate::language::Language;

#[derive(Clone, Debug)]
pub struct Word {
    pub text: String,
    pub word: Vec<u8>,
    pub pattern: Vec<u8>,
    pub candidates: Option<Vec<Candidate>>,
    pub enabled: bool,
    pub firstcand: usize,
}

impl Word {
    pub fn new(text: String, word: Vec<u8>) -> Self {
        let pattern = Self::make_pattern(&word);
        Word {
            text,
            word,
            pattern,
            candidates: None,
            enabled: true,
            firstcand: 0,
        }
    }

    pub fn make_pattern(word: &[u8]) -> Vec<u8> {
        let mut pattern = vec![0u8; word.len()];
        let mut next: u16 = 1;
        let mut map = [0u16; 256];
        for (i, &c) in word.iter().enumerate() {
            let idx = c as usize;
            if map[idx] == 0 {
                map[idx] = next;
                next += 1;
            }
            pattern[i] = (map[idx] - 1) as u8;
        }
        pattern
    }

    pub fn compare_arrays(a: &[u8], b: &[u8]) -> Ordering {
        match a.len().cmp(&b.len()) {
            Ordering::Equal => {}
            ord => return ord,
        }
        for (ai, bi) in a.iter().zip(b.iter()) {
            match ai.cmp(bi) {
                Ordering::Equal => continue,
                ord => return ord,
            }
        }
        Ordering::Equal
    }

    #[cfg(test)]
    pub fn compare_text(&self, other: &Word) -> Ordering {
        self.text.cmp(&other.text)
    }

    #[cfg(test)]
    pub fn compare_pattern(&self, other: &Word) -> Ordering {
        let ord = Self::compare_arrays(&self.pattern, &other.pattern);
        if ord != Ordering::Equal {
            return ord;
        }
        self.text.cmp(&other.text)
    }

    pub fn compare_word(&self, other: &Word) -> Ordering {
        Self::compare_arrays(&self.word, &other.word)
    }
}

#[derive(Clone, Debug)]
pub struct Candidate {
    pub cand: Vec<u8>,
    pub score: f64,
}

pub fn to_candidates(words: Vec<Vec<u8>>, lang: &(impl Language + ?Sized)) -> Vec<Candidate> {
    let mut candidates = Vec::with_capacity(words.len());
    for w in words {
        let score = lang.compute_candidate_score(&w);
        candidates.push(Candidate { cand: w, score });
    }
    sort_candidates(&mut candidates);
    candidates
}

pub fn sort_candidates(candidates: &mut [Candidate]) {
    candidates.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap());
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_make_pattern_abab() {
        let word = b"ABAB";
        let pattern = Word::make_pattern(word);
        assert_eq!(pattern, vec![0, 1, 0, 1]);
    }

    #[test]
    fn test_make_pattern_abc() {
        let word = b"ABC";
        let pattern = Word::make_pattern(word);
        assert_eq!(pattern, vec![0, 1, 2]);
    }

    #[test]
    fn test_make_pattern_repeated() {
        let word = b"AAAA";
        let pattern = Word::make_pattern(word);
        assert_eq!(pattern, vec![0, 0, 0, 0]);
    }

    #[test]
    fn test_make_pattern_empty() {
        let word: &[u8] = b"";
        let pattern = Word::make_pattern(word);
        assert!(pattern.is_empty());
    }

    #[test]
    fn test_make_pattern_non_consecutive() {
        let word = b"ABCA";
        let pattern = Word::make_pattern(word);
        assert_eq!(pattern, vec![0, 1, 2, 0]);
    }

    #[test]
    fn test_compare_arrays_equal() {
        assert_eq!(
            Word::compare_arrays(b"abc", b"abc"),
            Ordering::Equal
        );
    }

    #[test]
    fn test_compare_arrays_shorter_first() {
        assert_eq!(
            Word::compare_arrays(b"ab", b"abc"),
            Ordering::Less
        );
    }

    #[test]
    fn test_compare_arrays_longer_first() {
        assert_eq!(
            Word::compare_arrays(b"abc", b"ab"),
            Ordering::Greater
        );
    }

    #[test]
    fn test_compare_arrays_byte_less() {
        assert_eq!(
            Word::compare_arrays(b"aba", b"abc"),
            Ordering::Less
        );
    }

    #[test]
    fn test_compare_arrays_byte_greater() {
        assert_eq!(
            Word::compare_arrays(b"abd", b"abc"),
            Ordering::Greater
        );
    }

    #[test]
    fn test_compare_arrays_empty_equal() {
        assert_eq!(
            Word::compare_arrays(b"", b""),
            Ordering::Equal
        );
    }

    #[test]
    fn test_compare_text() {
        let a = Word::new("alpha".to_string(), b"ABC".to_vec());
        let b = Word::new("beta".to_string(), b"ABC".to_vec());
        assert_eq!(a.compare_text(&b), Ordering::Less);
        assert_eq!(b.compare_text(&a), Ordering::Greater);
        assert_eq!(a.compare_text(&a), Ordering::Equal);
    }

    #[test]
    fn test_compare_pattern_same_pattern_different_text() {
        let a = Word::new("a".to_string(), b"ABA".to_vec());
        let b = Word::new("b".to_string(), b"CDC".to_vec());
        assert_eq!(a.compare_pattern(&b), Ordering::Less);
    }

    #[test]
    fn test_compare_pattern_different() {
        let a = Word::new("a".to_string(), b"ABC".to_vec());
        let b = Word::new("b".to_string(), b"ABA".to_vec());
        assert_eq!(a.compare_pattern(&b), Ordering::Greater);
    }

    #[test]
    fn test_compare_word_equal() {
        let a = Word::new("a".to_string(), b"ABC".to_vec());
        let b = Word::new("b".to_string(), b"ABC".to_vec());
        assert_eq!(a.compare_word(&b), Ordering::Equal);
    }

    #[test]
    fn test_compare_word_different() {
        let a = Word::new("a".to_string(), b"ABC".to_vec());
        let b = Word::new("b".to_string(), b"ABD".to_vec());
        assert_eq!(a.compare_word(&b), Ordering::Less);
    }

    #[test]
    fn test_compare_word_length() {
        let a = Word::new("a".to_string(), b"AB".to_vec());
        let b = Word::new("b".to_string(), b"ABC".to_vec());
        assert_eq!(a.compare_word(&b), Ordering::Less);
    }

    #[test]
    fn test_make_pattern_handles_byte_256_indices() {
        let mut word = Vec::new();
        for i in 0u8..=255 {
            word.push(i);
        }
        let pattern = Word::make_pattern(&word);
        assert_eq!(pattern.len(), 256);
        for (i, &p) in pattern.iter().enumerate() {
            assert_eq!(p as usize, i);
        }
        assert_eq!(pattern[0], 0);
        assert_eq!(pattern[255], 255);
    }

    #[test]
    fn test_clone() {
        let a = Word::new("test".to_string(), b"ABCABC".to_vec());
        let b = a.clone();
        assert_eq!(a.text, b.text);
        assert_eq!(a.word, b.word);
        assert_eq!(a.pattern, b.pattern);
    }

    #[test]
    fn test_sort_candidates_descending() {
        let mut v = vec![
            Candidate { cand: b"zzz".to_vec(), score: 1.0 },
            Candidate { cand: b"aaa".to_vec(), score: 3.5 },
            Candidate { cand: b"mmm".to_vec(), score: 2.0 },
            Candidate { cand: b"bbb".to_vec(), score: -0.5 },
        ];
        sort_candidates(&mut v);
        let scores: Vec<f64> = v.iter().map(|c| c.score).collect();
        assert_eq!(scores, vec![3.5, 2.0, 1.0, -0.5]);
    }

    #[test]
    fn test_sort_candidates_empty() {
        let mut v: Vec<Candidate> = vec![];
        sort_candidates(&mut v);
        assert!(v.is_empty());
    }

    #[test]
    fn test_sort_candidates_single() {
        let mut v = vec![Candidate { cand: b"hello".to_vec(), score: 42.0 }];
        sort_candidates(&mut v);
        assert_eq!(v[0].score, 42.0);
        assert_eq!(v[0].cand, b"hello");
    }

    #[test]
    fn test_sort_candidates_ties() {
        let mut v = vec![
            Candidate { cand: b"b".to_vec(), score: 1.0 },
            Candidate { cand: b"a".to_vec(), score: 1.0 },
        ];
        sort_candidates(&mut v);
        let scores: Vec<f64> = v.iter().map(|c| c.score).collect();
        assert_eq!(scores, vec![1.0, 1.0]);
    }

    #[test]
    fn test_sort_candidates_stable_by_score() {
        let mut v = vec![
            Candidate { cand: b"first".to_vec(), score: 5.0 },
            Candidate { cand: b"second".to_vec(), score: 3.0 },
            Candidate { cand: b"third".to_vec(), score: 5.0 },
            Candidate { cand: b"fourth".to_vec(), score: 5.0 },
        ];
        sort_candidates(&mut v);
        let scores: Vec<f64> = v.iter().map(|c| c.score).collect();
        assert_eq!(scores, vec![5.0, 5.0, 5.0, 3.0]);
        assert_eq!(v[0].cand, b"first");
    }

    #[test]
    fn test_sort_candidates_negative_and_nan() {
        let mut v = vec![
            Candidate { cand: b"neg".to_vec(), score: -10.0 },
            Candidate { cand: b"pos".to_vec(), score: 10.0 },
            Candidate { cand: b"zero".to_vec(), score: 0.0 },
        ];
        sort_candidates(&mut v);
        let scores: Vec<f64> = v.iter().map(|c| c.score).collect();
        assert_eq!(scores, vec![10.0, 0.0, -10.0]);
    }
}
