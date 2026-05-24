pub struct DecryptoParameters {
    pub prune_each_node: bool,
    pub prune_root_node: bool,
    pub enable_single_letters: bool,
    pub max_reduce_iterations: usize,
    pub max_random_word_disable: usize,
    pub max_random_word_iterations: usize,
}

impl Default for DecryptoParameters {
    fn default() -> Self {
        Self {
            prune_each_node: true,
            prune_root_node: true,
            enable_single_letters: true,
            max_reduce_iterations: 50,
            max_random_word_disable: 3,
            max_random_word_iterations: 1000,
        }
    }
}
