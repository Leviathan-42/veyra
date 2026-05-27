use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Account {
    pub access_token: String,
    pub refresh_token: String,
    pub expires_at: i64,
    pub profile: MinecraftProfile,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PublicAccount {
    pub profile: MinecraftProfile,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MinecraftProfile {
    pub id: String,
    pub name: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct AuthStart {
    pub auth_url: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct AuthCodePayload {
    pub code: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct VersionChoice {
    pub id: String,
    pub version_type: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct LaunchResult {
    pub pid: u32,
}

#[derive(Debug, Clone, Serialize)]
pub struct InstalledMod {
    pub file_name: String,
    pub size_bytes: u64,
}
