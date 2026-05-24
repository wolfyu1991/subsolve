use std::sync::OnceLock;

use crate::ngram4::Ngram4;
use crate::puzzle::Puzzle;

pub struct Precomputed {
    pub pos_by_cipher: Vec<Vec<usize>>,
    pub context: Vec<[usize; 4]>,
}

fn precompute(puzzle: &Puzzle) -> Precomputed {
    let trust_spaces = puzzle.trust_spaces;
    let cb = &puzzle.cipher_bytes;
    let len = cb.len();
    let lc = puzzle.lang.letter_count();

    let mut pos_by_cipher: Vec<Vec<usize>> = vec![Vec::new(); lc];
    let mut context: Vec<[usize; 4]> = Vec::with_capacity(len);

    for i in 0..len {
        let entry = if trust_spaces {
            match i {
                0 => [
                    if len >= 2 { cb[len - 2] as usize } else { lc },
                    if len >= 1 { cb[len - 1] as usize } else { lc },
                    lc,
                    cb[0] as usize,
                ],
                1 => [
                    if len >= 1 { cb[len - 1] as usize } else { lc },
                    lc,
                    cb[0] as usize,
                    cb[1] as usize,
                ],
                2 => [lc, cb[0] as usize, cb[1] as usize, cb[2] as usize],
                _ => [
                    cb[i - 3] as usize,
                    cb[i - 2] as usize,
                    cb[i - 1] as usize,
                    cb[i] as usize,
                ],
            }
        } else {
            match i {
                0 => [
                    if len >= 3 { cb[len - 3] as usize } else { lc },
                    if len >= 2 { cb[len - 2] as usize } else { lc },
                    if len >= 1 { cb[len - 1] as usize } else { lc },
                    cb[0] as usize,
                ],
                1 => [
                    if len >= 2 { cb[len - 2] as usize } else { lc },
                    if len >= 1 { cb[len - 1] as usize } else { lc },
                    cb[0] as usize,
                    cb[1] as usize,
                ],
                2 => [
                    if len >= 1 { cb[len - 1] as usize } else { lc },
                    cb[0] as usize,
                    cb[1] as usize,
                    cb[2] as usize,
                ],
                _ => [
                    cb[i - 3] as usize,
                    cb[i - 2] as usize,
                    cb[i - 1] as usize,
                    cb[i] as usize,
                ],
            }
        };
        context.push(entry);
        for &v in &entry {
            if v < lc {
                pos_by_cipher[v].push(i);
            }
        }
    }

    Precomputed { pos_by_cipher, context }
}

pub struct GAProblem {
    pub puzzle: Puzzle,
    precomputed: OnceLock<Precomputed>,
}

impl GAProblem {
    pub fn new(puzzle: Puzzle) -> Self {
        GAProblem {
            puzzle,
            precomputed: OnceLock::new(),
        }
    }

    pub fn precomputed(&self) -> &Precomputed {
        self.precomputed
            .get_or_init(|| precompute(&self.puzzle))
    }

    fn grams(&self) -> &Ngram4 {
        if self.puzzle.trust_spaces {
            self.puzzle.lang.tetragrams()
        } else {
            self.puzzle.lang.tetragrams_ns()
        }
    }

    pub fn compute_score(&self, map: &[i32]) -> f32 {
        let grams: &Ngram4 = self.grams();
        let ctx = &self.precomputed().context;

        let mut ma = map[ctx[0][0]];
        let mut mb = map[ctx[0][1]];
        let mut mc = map[ctx[0][2]];

        let mut score = 0.0f32;
        for i in 0..ctx.len() {
            let md = map[ctx[i][3]];
            score += grams.get(ma as usize, mb as usize, mc as usize, md as usize);
            if i + 1 < ctx.len() {
                ma = map[ctx[i + 1][0]];
                mb = map[ctx[i + 1][1]];
                mc = map[ctx[i + 1][2]];
            }
        }

        score
    }

    pub fn compute_score_delta(
        &self,
        map: &[i32],
        a: usize,
        b: usize,
        affected: &[usize],
    ) -> f32 {
        let grams: &Ngram4 = self.grams();
        let ctx = &self.precomputed().context;
        let au = a;
        let bu = b;

        let mut delta = 0.0f32;
        for &pos in affected {
            let entry = &ctx[pos];

            let c0 = map[entry[0]];
            let c1 = map[entry[1]];
            let c2 = map[entry[2]];
            let c3 = map[entry[3]];
            let old = grams.get(c0 as usize, c1 as usize, c2 as usize, c3 as usize);

            let pick = |idx: usize| -> i32 {
                if entry[idx] == au { map[b] }
                else if entry[idx] == bu { map[a] }
                else { map[entry[idx]] }
            };
            let n0 = pick(0);
            let n1 = pick(1);
            let n2 = pick(2);
            let n3 = pick(3);
            let new = grams.get(n0 as usize, n1 as usize, n2 as usize, n3 as usize);

            delta += new - old;
        }
        delta
    }
}

#[cfg(test)]
mod ga_tests {
    use super::*;
    use crate::dict::Dict;

    fn make_test_puzzle() -> Puzzle {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let ciphertext = "KHA VKU".to_string();
        let clues = "".to_string();
        Puzzle::new(dict, &ciphertext, &clues, false, true)
    }

    fn identity_map(n: usize) -> Vec<i32> {
        let mut map = vec![0i32; n + 1];
        for i in 0..n {
            map[i] = i as i32;
        }
        map[n] = n as i32;
        map
    }

    #[test]
    fn test_compute_score_identity_map() {
        let puzzle = make_test_puzzle();
        let ga = GAProblem::new(puzzle);
        let n = ga.puzzle.lang.letter_count();
        let map = identity_map(n);
        let score = ga.compute_score(&map);
        assert!(score.is_finite());
    }

    #[test]
    fn test_compute_score_different_map() {
        let puzzle = make_test_puzzle();
        let ga = GAProblem::new(puzzle);
        let n = ga.puzzle.lang.letter_count();
        let mut map = identity_map(n);
        for i in 0..n {
            map[i] = ((i + 1) % n) as i32;
        }
        let score = ga.compute_score(&map);
        assert!(score.is_finite());
    }

    fn collect_affected(pre: &Precomputed, a: usize, b: usize, len: usize) -> Vec<usize> {
        let mut bm = vec![false; len];
        let mut affected = Vec::new();
        for &pos in &pre.pos_by_cipher[a] {
            if !bm[pos] {
                bm[pos] = true;
                affected.push(pos);
            }
        }
        for &pos in &pre.pos_by_cipher[b] {
            if !bm[pos] {
                bm[pos] = true;
                affected.push(pos);
            }
        }
        affected
    }

    #[test]
    fn test_delta_matches_full_score() {
        let puzzle = make_test_puzzle();
        let ga = GAProblem::new(puzzle);
        let n = ga.puzzle.lang.letter_count();
        let mut map = identity_map(n);

        let a = 0usize;
        let b = 5usize;
        let full_before = ga.compute_score(&map);

        let pre = ga.precomputed();
        let affected = collect_affected(&pre, a, b, ga.puzzle.cipher_bytes.len());
        let delta = ga.compute_score_delta(&map, a, b, &affected);

        map.swap(a, b);
        let full_after = ga.compute_score(&map);

        assert!(
            (full_after - full_before - delta).abs() < 1e-5f32,
            "delta {:.6} != actual diff {:.6}",
            delta,
            full_after - full_before
        );
    }

    #[test]
    fn test_delta_multiple_swaps() {
        let puzzle = make_test_puzzle();
        let ga = GAProblem::new(puzzle);
        let n = ga.puzzle.lang.letter_count();
        let mut map = identity_map(n);
        let pre = ga.precomputed();
        let len = ga.puzzle.cipher_bytes.len();

        let swaps = [(0, 3), (1, 7), (2, 10), (0, 15)];
        for &(a, b) in &swaps {
            let full_before = ga.compute_score(&map);
            let affected = collect_affected(&pre, a, b, len);
            let delta = ga.compute_score_delta(&map, a, b, &affected);

            map.swap(a, b);
            let full_after = ga.compute_score(&map);

            assert!(
                (full_after - full_before - delta).abs() < 1e-5f32,
                "swap ({},{}) delta {:.6} != actual {:.6}",
                a,
                b,
                delta,
                full_after - full_before
            );
        }
    }

    #[test]
    fn test_precomputed_context_vs_original_method() {
        let puzzle = make_test_puzzle();
        let ga = GAProblem::new(puzzle);
        let n = ga.puzzle.lang.letter_count();
        let map = identity_map(n);
        let pre = ga.precomputed();
        let ctx = &pre.context;
        let len = ga.puzzle.cipher_bytes.len();

        let lc = ga.puzzle.lang.letter_count();
        let mut ca = lc;
        let mut cb = lc;
        let mut cc = lc;
        if ga.puzzle.trust_spaces {
            if len > 2 { ca = ga.puzzle.cipher_bytes[len - 2] as usize; }
            if len > 1 { cb = ga.puzzle.cipher_bytes[len - 1] as usize; }
            cc = lc;
        } else {
            if len > 3 { ca = ga.puzzle.cipher_bytes[len - 3] as usize; }
            if len > 2 { cb = ga.puzzle.cipher_bytes[len - 2] as usize; }
            if len > 1 { cc = ga.puzzle.cipher_bytes[len - 1] as usize; }
        }

        let mut ma_orig = map[ca];
        let mut mb_orig = map[cb];
        let mut mc_orig = map[cc];

        for i in 0..len {
            let entry = &ctx[i];
            let ma_pre = map[entry[0]];
            let mb_pre = map[entry[1]];
            let mc_pre = map[entry[2]];
            let md_pre = map[entry[3]];

            assert_eq!(
                (ma_pre, mb_pre, mc_pre, md_pre),
                (ma_orig, mb_orig, mc_orig, map[ga.puzzle.cipher_bytes[i] as usize]),
                "context mismatch at position {i}"
            );

            let cd = ga.puzzle.cipher_bytes[i] as usize;
            let md_orig = map[cd];
            ma_orig = mb_orig;
            mb_orig = mc_orig;
            mc_orig = md_orig;
        }
    }
}
