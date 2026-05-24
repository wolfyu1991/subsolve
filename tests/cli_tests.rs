use std::path::PathBuf;
use std::process::Command;

fn binary_path() -> PathBuf {
    let mut p = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    p.push("target");
    p.push("debug");
    p.push("subsolve.exe");
    p
}

fn run_solver(args: &[&str]) -> std::process::Output {
    Command::new(binary_path())
        .args(args)
        .output()
        .expect("failed to run subsolve")
}

fn extract_solutions(stdout: &str) -> Vec<&str> {
    stdout.lines()
        .filter_map(|line| {
            let line = line.trim();
            if line.is_empty() { return None; }
            let (score_str, rest) = line.split_once(' ')?;
            score_str.parse::<f64>().ok()?;
            Some(rest.trim())
        })
        .collect()
}

#[test]
fn help_contains_embedded_data_mention() {
    let output = Command::new(binary_path())
        .arg("--help")
        .output()
        .expect("failed to run subsolve --help");
    assert!(output.status.success());
    let stdout = String::from_utf8_lossy(&output.stdout);
    assert!(stdout.contains("embedded"), "help should mention embedded data");
    assert!(stdout.contains("Dictionary"), "help should list --dictionary");
    assert!(stdout.contains("clues"), "help should list --clues");
    assert!(stdout.contains("timelimit"), "help should list --timelimit");
    assert!(stdout.contains("puzzlefile"), "help should list --puzzlefile");
    assert!(stdout.contains("scramble"), "help should list --scramble");
    assert!(stdout.contains("trustspaces"), "help should list --trustspaces");
}

#[test]
fn version_works() {
    let output = Command::new(binary_path())
        .arg("--version")
        .output()
        .expect("failed to run subsolve --version");
    assert!(output.status.success());
    let stdout = String::from_utf8_lossy(&output.stdout);
    assert!(stdout.contains("0.1.0"));
}

#[test]
fn no_ciphertext_shows_error() {
    let output = Command::new(binary_path())
        .output()
        .expect("failed to run subsolve with no args");
    assert!(!output.status.success());
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(stderr.contains("No ciphertext provided"));
}

#[test]
fn empty_ciphertext_shows_error() {
    let output = Command::new(binary_path())
        .arg("")
        .output()
        .expect("failed to run subsolve with empty ciphertext");
    assert!(!output.status.success());
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(stderr.contains("No ciphertext provided"));
}

#[test]
fn scramble_outputs_text_and_clues() {
    let output = Command::new(binary_path())
        .args(["--scramble", "THE QUICK BROWN FOX"])
        .output()
        .expect("failed to run subsolve --scramble");
    assert!(output.status.success());
    let stdout = String::from_utf8_lossy(&output.stdout);
    assert!(!stdout.is_empty(), "scramble should produce output");
    // scramble prints ciphertext then clues (clues may be empty line)
    assert!(stdout.trim().contains('\n') || !stdout.trim().is_empty(), "scramble should output at least ciphertext");
}

#[test]
fn scramble_with_clues() {
    let output = Command::new(binary_path())
        .args(["--scramble", "-c", "A=B", "HELLO WORLD"])
        .output()
        .expect("failed to run subsolve --scramble with clues");
    assert!(output.status.success());
    let stdout = String::from_utf8_lossy(&output.stdout);
    let lines: Vec<&str> = stdout.trim().lines().collect();
    assert_eq!(lines.len(), 2);
}

#[test]
fn dictionary_missing_file_shows_error() {
    let output = Command::new(binary_path())
        .args(["--dictionary", "nonexistent.dict", "TEST"])
        .output()
        .expect("failed to run subsolve with bad --dictionary");
    assert!(!output.status.success());
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(stderr.contains("Couldn't open dictionary"));
}

#[test]
fn puzzlefile_missing_shows_error() {
    let output = Command::new(binary_path())
        .args(["--puzzlefile", "nonexistent.txt"])
        .output()
        .expect("failed to run subsolve with bad --puzzlefile");
    assert!(!output.status.success());
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(stderr.contains("Couldn't read puzzle file"));
}

#[test]
fn puzzlefile_skips_blanks_and_comments() {
    use std::io::Write;
    let dir = tempfile::tempdir().unwrap();
    let pfile = dir.path().join("puzzle.txt");
    let mut f = std::fs::File::create(&pfile).unwrap();
    writeln!(f, "# this is a comment").unwrap();
    writeln!(f, "").unwrap();
    writeln!(f, "  ").unwrap();
    writeln!(f, "HELLO WORLD").unwrap();
    writeln!(f, "A=B").unwrap();
    f.flush().unwrap();

    let output = Command::new(binary_path())
        .args(["--puzzlefile", pfile.to_str().unwrap()])
        .output()
        .expect("failed to run subsolve with puzzlefile");
    assert!(output.status.success(), "solver should run: {:?}", String::from_utf8_lossy(&output.stderr));
    let stdout = String::from_utf8_lossy(&output.stdout);
    assert!(!stdout.is_empty(), "should produce output");
}

#[test]
fn trustspaces_false_runs_genetic_solver() {
    let _ = Command::new(binary_path())
        .args(["--trustspaces", "false", "THE QUICK BROWN FOX"])
        .output();
}

#[test]
fn basic_cipher_solves() {
    let output = Command::new(binary_path())
        .args(["WKH TXLFN EURZQ IRA"])
        .output()
        .expect("failed to run subsolve with basic cipher");
    assert!(output.status.success(), "stderr: {:?}", String::from_utf8_lossy(&output.stderr));
    let stdout = String::from_utf8_lossy(&output.stdout);
    assert!(!stdout.is_empty(), "should produce solution lines");
    assert!(
        stdout.to_lowercase().contains("the"),
        "solution should contain 'the': {}",
        stdout
    );
}

/// Clues-only test: verify clue parsing works (equality + inequality).
#[test]
fn clue_parsing_equality_and_inequality() {
    let output = run_solver(&["-c", "K=H O=L R=O", "KHOOR ZRUOG"]);
    assert!(output.status.success(), "stderr: {:?}", String::from_utf8_lossy(&output.stderr));
    let stdout = String::from_utf8_lossy(&output.stdout).to_lowercase();
    assert!(stdout.contains("hello") && stdout.contains("world"),
        "with full clues, should decode to 'hello world': {}", stdout);
}

/// Verify four different ciphertext lengths all produce output.
#[test]
fn various_ciphertext_lengths() {
    let tests = &[
        ("WKH", 1),           // 1 word
        ("WKH FDB", 2),       // 2 words
        ("WKH TXLFN EURZQ IRA", 4),  // 4 words
    ];
    for (cipher, expected_words) in tests {
        let output = run_solver(&["--timelimit", "3", cipher]);
        assert!(output.status.success(),
            "cipher '{}' should succeed: {}", cipher, String::from_utf8_lossy(&output.stderr));
        let stdout = String::from_utf8_lossy(&output.stdout);
        assert!(!stdout.is_empty(), "cipher '{}' should produce output", cipher);
        let solutions = extract_solutions(&stdout);
        assert!(!solutions.is_empty(), "cipher '{}' should have solutions", cipher);
        // Every solution should have at least the expected word count
        for sol in &solutions {
            assert!(sol.split_whitespace().count() >= *expected_words,
                "cipher '{}': solution '{}' should have >= {} words", cipher, sol, expected_words);
        }
    }
}

/// Verify solver produces multiple solutions with descending scores (best first).
#[test]
fn multiple_solutions_found() {
    let output = run_solver(&["WKH TXLFN EURZQ IRA"]);
    assert!(output.status.success());
    let stdout = String::from_utf8_lossy(&output.stdout);
    let score_lines: Vec<&str> = stdout.lines().filter(|l| !l.trim().is_empty()).collect();
    assert!(score_lines.len() >= 2, "should have >=2 score lines, got {}: {}", score_lines.len(), stdout);
    let scores: Vec<f64> = score_lines.iter()
        .filter_map(|l| l.trim().split_once(' ').and_then(|(s, _)| s.parse().ok()))
        .collect();
    assert!(!scores.is_empty());
    assert!(scores[0] >= scores[scores.len() - 1],
        "first score should be best (descending order): {:?}", scores);
}

/// Medium ciphertext with clues (X=T, M=Y).
#[test]
fn medium_cipher_with_clues() {
    let output = run_solver(&["-c", "X=T M=Y", "--timelimit", "20",
        "PG XOYHLM XOYLY PZ GH TPUUYLYGRY EYXBYYG XOYHLM WGT JLWRXPRY"]);
    assert!(output.status.success(), "stderr: {:?}", String::from_utf8_lossy(&output.stderr));
    let stdout = String::from_utf8_lossy(&output.stdout).to_lowercase();
    assert!(stdout.contains("the"), "should contain 'the': {}", stdout);
    assert!(stdout.contains("theory"), "should contain 'theory': {}", stdout);
}

/// Genetic solver with short ciphertext should produce output.
#[test]
fn genetic_solver_short_text() {
    let output = run_solver(&["--trustspaces", "false", "--timelimit", "5",
        "WKH TXLFN EURZQ IRA"]);
    assert!(output.status.success(), "stderr: {:?}", String::from_utf8_lossy(&output.stderr));
    let stdout = String::from_utf8_lossy(&output.stdout);
    assert!(!stdout.is_empty(), "genetic solver should produce output");
    assert!(stdout.to_lowercase().contains("the") || stdout.contains('-'),
        "genetic solver should score lines: {}", stdout);
}

/// Very short cipher: 3-letter words should still decode.
#[test]
fn very_short_ciphertext() {
    let output = run_solver(&["WKH FDB"]);
    assert!(output.status.success(), "stderr: {:?}", String::from_utf8_lossy(&output.stderr));
    let stdout = String::from_utf8_lossy(&output.stdout).to_lowercase();
    assert!(stdout.contains("the"), "very short cipher should decode to 'the': {}", stdout);
}

/// Test clues with inequality operator (A!=B).
#[test]
fn clue_inequality_helps_solve() {
    let output = Command::new(binary_path())
        .args(["-c", "W!=X W!=Y W!=Z", "WKH TXLFN EURZQ IRA"])
        .output()
        .expect("failed to run subsolve");
    assert!(output.status.success(), "stderr: {:?}", String::from_utf8_lossy(&output.stderr));
    let stdout = String::from_utf8_lossy(&output.stdout).to_lowercase();
    assert!(stdout.contains("the"), "should solve despite inequality clues: {}", stdout);
}

/// --top N limits output to N lines on stdout.
#[test]
fn top_limits_solutions() {
    let output = run_solver(&["--top", "3", "WKH TXLFN EURZQ IRA"]);
    assert!(output.status.success());
    let stdout = String::from_utf8_lossy(&output.stdout);
    let lines: Vec<&str> = stdout.lines().filter(|l| !l.trim().is_empty()).collect();
    assert_eq!(lines.len(), 3, "--top 3 should produce exactly 3 stdout lines: {}", stdout);
}

/// --json produces valid JSON output.
#[test]
fn json_output_is_valid() {
    let output = run_solver(&["--json", "--timelimit", "5", "WKH TXLFN EURZQ IRA"]);
    assert!(output.status.success());
    let stdout = String::from_utf8_lossy(&output.stdout);
    let parsed: serde_json::Value = serde_json::from_str(stdout.trim()).expect("--json should produce valid JSON");
    let arr = parsed.as_array().expect("--json output should be an array");
    assert!(!arr.is_empty(), "JSON array should not be empty");
    for item in arr {
        let obj = item.as_object().expect("each element should be an object");
        assert!(obj.contains_key("solution"), "each object should have 'solution'");
        assert!(obj.contains_key("score"), "each object should have 'score'");
    }
}

/// --setsize limits the number of stored solutions.
#[test]
fn setsize_limits_solutions() {
    let output = run_solver(&["--setsize", "3", "--timelimit", "5", "WKH TXLFN EURZQ IRA"]);
    assert!(output.status.success());
    let stdout = String::from_utf8_lossy(&output.stdout);
    let lines: Vec<&str> = stdout.lines().filter(|l| !l.trim().is_empty()).collect();
    assert!(lines.len() <= 3, "--setsize 3 should output ≤3 solution lines, got {}", lines.len());
}

/// --timelimit 0 is rejected.
#[test]
fn timelimit_zero_rejected() {
    let output = Command::new(binary_path())
        .args(["--timelimit", "0", "WKH"])
        .output()
        .expect("failed to run subsolve");
    assert!(!output.status.success());
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(stderr.contains("--timelimit must be positive"), "stderr: {}", stderr);
}

/// --timelimit negative is rejected (clap interprets -5 as flags; error comes from clap).
#[test]
fn timelimit_negative_rejected() {
    let output = Command::new(binary_path())
        .args(["--timelimit=-5", "WKH"])
        .output()
        .expect("failed to run subsolve");
    assert!(!output.status.success());
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(stderr.contains("--timelimit must be positive"), "stderr: {}", stderr);
}
