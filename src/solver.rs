use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Instant;

use crate::mapset::MapSet;

pub trait SolverListener: Send {
    fn on_message(&mut self, msg: &str);
    fn on_progress(&mut self, progress: f64);
    fn on_finished(&mut self, elapsed: f64);
    fn on_solution(&mut self, mapset: &MapSet, spaces: Option<Vec<i32>>, full_solution: bool, score: f64);
}

pub trait Solver: Send {
    fn add_listener(&mut self, listener: Box<dyn SolverListener>);
    fn start(&mut self, max_time: f64);
    fn stop(&mut self);
    fn elapsed_time(&self) -> f64;
}

pub struct StopFlag {
    pub stop: Arc<AtomicBool>,
    start_time: Instant,
    max_time: f64,
}

impl StopFlag {
    pub fn new(max_time: f64) -> Self {
        StopFlag {
            stop: Arc::new(AtomicBool::new(false)),
            start_time: Instant::now(),
            max_time,
        }
    }

    pub fn elapsed(&self) -> f64 {
        self.start_time.elapsed().as_secs_f64()
    }

    pub fn progress(&self) -> f64 {
        (self.elapsed() / self.max_time).min(1.0)
    }

    pub fn should_stop(&self) -> bool {
        self.stop.load(Ordering::Relaxed) || self.elapsed() >= self.max_time
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    #[test]
    fn test_new_stopflag() {
        let sf = StopFlag::new(10.0);
        assert!(!sf.stop.load(Ordering::Relaxed));
        assert!(sf.elapsed() < 1.0);
        assert!(!sf.should_stop());
    }

    #[test]
    fn test_elapsed_non_negative() {
        let sf = StopFlag::new(10.0);
        let elapsed = sf.elapsed();
        assert!(elapsed >= 0.0);
    }

    #[test]
    fn test_timeout_triggers_stop() {
        let sf = StopFlag::new(0.001);
        std::thread::sleep(Duration::from_millis(10));
        assert!(sf.should_stop());
    }

    #[test]
    fn test_external_stop_flag() {
        let sf = StopFlag::new(10.0);
        sf.stop.store(true, Ordering::Relaxed);
        assert!(sf.should_stop());
    }

    #[test]
    fn test_arc_sharing() {
        let sf = StopFlag::new(10.0);
        let stop_clone = sf.stop.clone();
        stop_clone.store(true, Ordering::Relaxed);
        assert!(sf.should_stop());
    }

    #[test]
    fn test_zero_timeout() {
        let sf = StopFlag::new(0.0);
        std::thread::sleep(Duration::from_millis(1));
        assert!(sf.should_stop());
    }
}
