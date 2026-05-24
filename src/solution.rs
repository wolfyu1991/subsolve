#[derive(Clone, Debug, serde::Serialize)]
pub struct Solution {
    pub solution: String,
    pub score: f64,
}

impl Solution {
    pub fn new(solution: String, score: f64) -> Self {
        Solution { solution, score }
    }
}

impl PartialOrd for Solution {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        other.score.partial_cmp(&self.score)
    }
}

impl PartialEq for Solution {
    fn eq(&self, other: &Self) -> bool {
        self.score == other.score && self.solution == other.solution
    }
}

#[derive(Clone, Debug)]
pub struct SolutionSet {
    set: Vec<Solution>,
    capacity: usize,
    worst: f64,
    best: f64,
}

impl SolutionSet {
    pub fn new(capacity: usize) -> Self {
        SolutionSet {
            set: Vec::with_capacity(capacity),
            capacity,
            worst: -f64::MAX,
            best: -f64::MAX,
        }
    }

    pub fn reset(&mut self) {
        self.set.clear();
        self.worst = -f64::MAX;
        self.best = -f64::MAX;
    }

    pub fn size(&self) -> usize {
        self.set.len()
    }

    pub fn get_worst(&self) -> f64 {
        self.worst
    }

    pub fn get_best(&self) -> f64 {
        self.best
    }

    pub fn get_element(&self, idx: usize) -> &Solution {
        &self.set[idx]
    }

    pub fn would_add(&self, score: f64) -> bool {
        self.set.len() < self.capacity || score > self.worst
    }

    pub fn add(&mut self, sol: Solution) -> bool {
        if self.set.len() >= self.capacity && sol.score <= self.worst {
            return false;
        }

        if self.set.iter().any(|s| s.solution.eq_ignore_ascii_case(&sol.solution)) {
            return false;
        }

        let pos = self
            .set
            .iter()
            .position(|s| s.score < sol.score)
            .unwrap_or(self.set.len());
        self.set.insert(pos, sol);

        if self.set.len() > self.capacity {
            self.set.pop();
        }

        self.worst = self.set.last().map(|s| s.score).unwrap_or(-f64::MAX);
        self.best = self.set.first().map(|s| s.score).unwrap_or(-f64::MAX);

        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_solution_new() {
        let s = Solution::new("hello".to_string(), 0.5);
        assert_eq!(s.solution, "hello");
        assert!((s.score - 0.5).abs() < 1e-10);
    }

    #[test]
    fn test_solution_partial_ord() {
        // Higher score = "less" for descending sort
        let high = Solution::new("high".to_string(), 0.9);
        let low = Solution::new("low".to_string(), 0.5);
        assert!(high < low);
    }

    #[test]
    fn test_solution_set_new_and_empty() {
        let ss = SolutionSet::new(5);
        assert_eq!(ss.size(), 0);
        assert_eq!(ss.get_worst(), -f64::MAX);
        assert_eq!(ss.get_best(), -f64::MAX);
    }

    #[test]
    fn test_solution_set_reset() {
        let mut ss = SolutionSet::new(5);
        ss.add(Solution::new("test".to_string(), 0.5));
        assert_eq!(ss.size(), 1);
        ss.reset();
        assert_eq!(ss.size(), 0);
        assert_eq!(ss.get_worst(), -f64::MAX);
        assert_eq!(ss.get_best(), -f64::MAX);
    }

    #[test]
    fn test_add_descending_order() {
        let mut ss = SolutionSet::new(5);
        ss.add(Solution::new("low".to_string(), 0.3));
        ss.add(Solution::new("high".to_string(), 0.9));
        ss.add(Solution::new("mid".to_string(), 0.6));

        assert_eq!(ss.size(), 3);
        assert!((ss.get_element(0).score - 0.9).abs() < 1e-10);
        assert!((ss.get_element(1).score - 0.6).abs() < 1e-10);
        assert!((ss.get_element(2).score - 0.3).abs() < 1e-10);
    }

    #[test]
    fn test_capacity_rejects_worst() {
        let mut ss = SolutionSet::new(3);
        ss.add(Solution::new("a".to_string(), 0.5));
        ss.add(Solution::new("b".to_string(), 0.7));
        ss.add(Solution::new("c".to_string(), 0.3));
        assert_eq!(ss.size(), 3);

        assert!(!ss.add(Solution::new("d".to_string(), 0.2)));
        assert_eq!(ss.size(), 3);
    }

    #[test]
    fn test_capacity_rejects_equal_worst() {
        let mut ss = SolutionSet::new(3);
        ss.add(Solution::new("a".to_string(), 0.5));
        ss.add(Solution::new("b".to_string(), 0.7));
        ss.add(Solution::new("c".to_string(), 0.3));
        assert_eq!(ss.size(), 3);

        assert!(!ss.add(Solution::new("d".to_string(), 0.3)));
        assert_eq!(ss.size(), 3);
    }

    #[test]
    fn test_replace_worst_with_better() {
        let mut ss = SolutionSet::new(3);
        ss.add(Solution::new("a".to_string(), 0.5));
        ss.add(Solution::new("b".to_string(), 0.7));
        ss.add(Solution::new("c".to_string(), 0.3));
        assert_eq!(ss.get_worst(), 0.3);

        assert!(ss.add(Solution::new("d".to_string(), 0.4)));
        assert_eq!(ss.size(), 3);
        assert!((ss.get_element(0).score - 0.7).abs() < 1e-10);
        assert!((ss.get_element(1).score - 0.5).abs() < 1e-10);
        assert!((ss.get_element(2).score - 0.4).abs() < 1e-10);
        assert!((ss.get_worst() - 0.4).abs() < 1e-10);
    }

    #[test]
    fn test_worst_best_tracking() {
        let mut ss = SolutionSet::new(5);
        ss.add(Solution::new("a".to_string(), 0.5));
        assert!((ss.get_best() - 0.5).abs() < 1e-10);
        assert!((ss.get_worst() - 0.5).abs() < 1e-10);

        ss.add(Solution::new("b".to_string(), 0.9));
        assert!((ss.get_best() - 0.9).abs() < 1e-10);
        assert!((ss.get_worst() - 0.5).abs() < 1e-10);

        ss.add(Solution::new("c".to_string(), 0.3));
        assert!((ss.get_best() - 0.9).abs() < 1e-10);
        assert!((ss.get_worst() - 0.3).abs() < 1e-10);
    }

    #[test]
    fn test_would_add_before_full() {
        let ss = SolutionSet::new(3);
        assert!(ss.would_add(0.5));
        assert!(ss.would_add(-1.0));
    }

    #[test]
    fn test_would_add_when_full() {
        let mut ss = SolutionSet::new(3);
        ss.add(Solution::new("a".to_string(), 0.5));
        ss.add(Solution::new("b".to_string(), 0.7));
        ss.add(Solution::new("c".to_string(), 0.3));
        assert_eq!(ss.size(), 3);

        assert!(!ss.would_add(0.2));
        assert!(!ss.would_add(0.3));
        assert!(ss.would_add(0.4));
        assert!(ss.would_add(0.8));
    }

    #[test]
    fn test_get_element_ref() {
        let mut ss = SolutionSet::new(5);
        ss.add(Solution::new("hello".to_string(), 1.0));
        let el = ss.get_element(0);
        assert_eq!(el.solution, "hello");
        assert!((el.score - 1.0).abs() < 1e-10);
    }

    #[test]
    fn test_capacity_zero() {
        let mut ss = SolutionSet::new(0);
        // Java behavior: add returns true but element is immediately discarded
        assert!(ss.add(Solution::new("x".to_string(), 1.0)));
        assert_eq!(ss.size(), 0);
    }
}
