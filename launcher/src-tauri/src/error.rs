pub fn stringify(error: anyhow::Error) -> String {
    format!("{error:#}")
}
