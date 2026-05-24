const DIM: usize = 28;

pub struct Ngram4 {
    data: &'static [f32],
}

impl Ngram4 {
    pub fn from_raw(data: &'static [f32]) -> Self {
        Ngram4 { data }
    }

    pub fn get(&self, i: usize, j: usize, k: usize, w: usize) -> f32 {
        let idx = ((i * DIM + j) * DIM + k) * DIM + w;
        self.data[idx]
    }

    pub fn get_log_prob(&self, a: u8, b: u8, c: u8, d: u8) -> f64 {
        let idx = ((a as usize * DIM + b as usize) * DIM + c as usize) * DIM + d as usize;
        self.data[idx] as f64
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::resources;

    #[test]
    fn test_english4() {
        let ng = Ngram4::from_raw(resources::tetragrams_raw());
        assert_eq!(ng.data.len(), DIM * DIM * DIM * DIM);
        let mut all_finite = true;
        let mut any_negative = false;
        for &v in ng.data {
            if !v.is_finite() {
                all_finite = false;
            }
            if v < 0.0 {
                any_negative = true;
            }
        }
        assert!(all_finite, "english4.sta contains NaN or Inf");
        assert!(any_negative, "english4.sta should contain negative log probs");
    }

    #[test]
    fn test_english4ns() {
        let ng = Ngram4::from_raw(resources::tetragrams_ns_raw());
        assert_eq!(ng.data.len(), DIM * DIM * DIM * DIM);
        let mut all_finite = true;
        let mut any_negative = false;
        for &v in ng.data {
            if !v.is_finite() {
                all_finite = false;
            }
            if v < 0.0 {
                any_negative = true;
            }
        }
        assert!(all_finite, "english4ns.sta contains NaN or Inf");
        assert!(any_negative, "english4ns.sta should contain negative log probs");
    }

    #[test]
    fn test_get_log_prob() {
        let ng = Ngram4::from_raw(resources::tetragrams_raw());
        let val = ng.get_log_prob(0, 1, 2, 3);
        assert!(val.is_finite());
    }
}
