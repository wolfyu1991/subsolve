use std::io::Read;

use clap::Parser;

use subsolve::dict::Dict;
use subsolve::dict_attack::DictionaryAttackSolver;
use subsolve::params::DecryptoParameters;
use subsolve::genetic::{GAProblem, GeneticSolver};
use subsolve::language::EnglishLanguage;
use subsolve::language::Language;
use subsolve::mapset::MapSet;
use subsolve::puzzle::Puzzle;
use subsolve::resources;
use subsolve::solution::{Solution, SolutionSet};
use subsolve::solver::{Solver, SolverListener};

#[derive(Parser)]
#[command(
    name = "subsolve",
    version = "0.1.0",
    about = "Cryptogram solver",
    long_about = "Substitution-cipher solver (port of Decrypto 8.5).\n\
                   \n\
                   Dictionary, tetragram and 5-gram data files are embedded;\n\
                   no external data files are needed at runtime.\n\
                   Use --dictionary only to override with a custom dictionary."
)]
struct Args {
    #[arg(long, help = "Custom dictionary file (optional; embedded dict used by default)")]
    dictionary: Option<String>,

    #[arg(short, long, default_value = "", help = "Known clue mappings (e.g. A=B C!=D)")]
    clues: String,

    #[arg(long, default_value_t = 10.0, help = "Time limit in seconds")]
    timelimit: f64,

    #[arg(long, help = "Read puzzle from file (lines with '=' are clues, others ciphertext)")]
    puzzlefile: Option<String>,

    #[arg(long, help = "Scramble ciphertext for sharing (prints scrambled text and clues)")]
    scramble: bool,

    #[arg(long, default_value_t = true, action = clap::ArgAction::Set, help = "Trust spaces as word boundaries (default: true)")]
    trustspaces: bool,

    #[arg(long, default_value_t = 100, help = "Maximum number of solutions to store")]
    setsize: usize,

    #[arg(short, long, help = "Show solver progress messages")]
    verbose: bool,

    #[arg(long, default_value_t = 0, help = "Show top N solutions (0 = all)")]
    top: usize,

    #[arg(long, help = "Output solutions in JSON format")]
    json: bool,

    #[arg(long, help = "Prune search tree at root node (default: true)")]
    prune_root_node: Option<bool>,

    #[arg(long, help = "Prune search tree at each node (default: true)")]
    prune_each_node: Option<bool>,

    #[arg(long, help = "Enable single-letter mapping suggestions (default: true)")]
    enable_single_letters: Option<bool>,

    #[arg(long, default_value_t = 50, help = "Maximum reduce iterations")]
    max_reduce_iters: usize,

    #[arg(long, default_value_t = 3, help = "Maximum words to disable in random search")]
    max_random_disable: usize,

    #[arg(long, default_value_t = 1000, help = "Maximum random word iterations")]
    max_random_iters: usize,

    ciphertext: Option<String>,
}

struct CliListener {
    raw_cipher_text: String,
    lang: EnglishLanguage,
    solutions: SolutionSet,
    verbose: bool,
    json: bool,
    top: usize,
    last_progress: f64,
}

impl SolverListener for CliListener {
    fn on_message(&mut self, msg: &str) {
        if self.verbose {
            eprintln!("{}", msg);
        }
    }

    fn on_progress(&mut self, progress: f64) {
        if self.verbose && progress - self.last_progress >= 0.05 {
            self.last_progress = progress;
            eprintln!("Progress: {:.0}%", progress * 100.0);
        }
    }

    fn on_finished(&mut self, elapsed: f64) {
        let n = self.solutions.size();
        if self.json {
            let vec: Vec<&Solution> = (0..n).map(|i| self.solutions.get_element(i)).collect();
            serde_json::to_writer(std::io::stdout().lock(), &vec).unwrap();
            println!();
            return;
        }
        let limit = if self.top > 0 { self.top.min(n) } else { n };
        if n == 0 {
            eprintln!("No solutions stored (elapsed: {:.2}s)", elapsed);
            return;
        }
        let best = self.solutions.get_element(0);
        eprintln!("Found {} solutions in {:.2}s", n, elapsed);
        eprintln!("Best: {:.4} {}", best.score, best.solution);
        for i in 0..limit {
            let sol = self.solutions.get_element(i);
            println!("{:.4} {}", sol.score, sol.solution);
        }
    }

    fn on_solution(&mut self, mapset: &MapSet, spaces: Option<Vec<i32>>, _full_solution: bool, score: f64) {
        let solution = self.lang.translate_string(&self.raw_cipher_text, mapset, spaces.as_deref());
        self.solutions.add(Solution::new(solution, score));
    }
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();

    if args.timelimit <= 0.0 {
        return Err("--timelimit must be positive".into());
    }

    let dict = match &args.dictionary {
        Some(path) => Dict::from_file(path)
            .map_err(|e| format!("Couldn't open dictionary '{}': {}", path, e))?,
        None => Dict::from_static(resources::dictionary())
            .map_err(|e| format!("Couldn't load embedded dictionary: {}", e))?,
    };

    let (ciphertext, clues) = if let Some(pfile) = &args.puzzlefile {
        let content = std::fs::read_to_string(pfile)
            .map_err(|e| format!("Couldn't read puzzle file '{}': {}", pfile, e))?;
        let mut ct = String::new();
        let mut cs = String::new();
        for line in content.lines() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            if line.contains('=') {
                cs.push_str(line);
                cs.push(' ');
            } else {
                ct.push_str(line);
                ct.push(' ');
            }
        }
        (ct.trim().to_string(), cs.trim().to_string())
    } else if let Some(ct) = args.ciphertext {
        (ct, args.clues.clone())
    } else {
        let mut buf = String::new();
        if std::io::stdin().lock().read_to_string(&mut buf).is_ok() && !buf.trim().is_empty() {
            (buf.trim().to_string(), args.clues.clone())
        } else {
            return Err("No ciphertext provided".into());
        }
    };

    if ciphertext.is_empty() {
        return Err("No ciphertext provided".into());
    }

    if args.scramble {
        let mut puzzle = Puzzle::new(dict, &ciphertext, &clues, args.trustspaces, true);
        puzzle.scramble(false);
        println!("{}", puzzle.cipher_text);
        println!("{}", puzzle.clues);
        return Ok(());
    }

    let mut params = DecryptoParameters {
        max_reduce_iterations: args.max_reduce_iters,
        max_random_word_disable: args.max_random_disable,
        max_random_word_iterations: args.max_random_iters,
        ..Default::default()
    };
    if let Some(v) = args.prune_root_node { params.prune_root_node = v; }
    if let Some(v) = args.prune_each_node { params.prune_each_node = v; }
    if let Some(v) = args.enable_single_letters { params.enable_single_letters = v; }

    let make_listener = |ct: &str| -> Box<CliListener> {
        Box::new(CliListener {
            raw_cipher_text: ct.to_string(),
            lang: EnglishLanguage::new(),
            solutions: SolutionSet::new(args.setsize),
            verbose: args.verbose,
            json: args.json,
            top: args.top,
            last_progress: -1.0,
        })
    };

    if args.trustspaces {
        let puzzle = Puzzle::new(dict, &ciphertext, &clues, true, true);
        let mut solver = DictionaryAttackSolver::new(puzzle);
        solver.set_params(params);
        solver.add_listener(make_listener(&ciphertext));
        solver.start(args.timelimit);
    } else {
        let puzzle = Puzzle::new(dict, &ciphertext, &clues, false, true);
        let problem = GAProblem::new(puzzle);
        let mut solver = GeneticSolver::new(problem);
        solver.add_listener(make_listener(&ciphertext));
        solver.start(args.timelimit);
    }

    Ok(())
}
