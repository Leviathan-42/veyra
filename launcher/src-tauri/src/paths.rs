use anyhow::{anyhow, Result};
use std::path::PathBuf;

pub fn app_dir() -> Result<PathBuf> {
    let base = dirs::data_dir().ok_or_else(|| anyhow!("Could not resolve OS data directory"))?;
    let current = base.join("VeyraLauncher");
    let legacy = base.join("BlockTrackerLauncher");

    if current.exists() {
        return Ok(current);
    }

    if legacy.exists() {
        if std::fs::rename(&legacy, &current).is_ok() {
            return Ok(current);
        }

        return Ok(legacy);
    }

    Ok(current)
}

pub fn game_dir() -> Result<PathBuf> {
    Ok(app_dir()?.join("minecraft"))
}

pub fn auth_path() -> Result<PathBuf> {
    Ok(app_dir()?.join("account.json"))
}
