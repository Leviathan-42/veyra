use crate::{
    paths,
    types::{Account, AuthStart, MinecraftProfile, PublicAccount},
};
use anyhow::{anyhow, Context, Result};
use keyring::Entry;
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::fs;

const CLIENT_ID: &str = "00000000402b5328";
const SCOPE: &str = "XboxLive.signin offline_access";
const REDIRECT_URI: &str = "https://login.live.com/oauth20_desktop.srf";
const KEYRING_SERVICE: &str = "dev.blocktracker.launcher";
const KEYRING_USER: &str = "minecraft_refresh_token";

#[derive(Debug, Deserialize)]
struct TokenResponse {
    access_token: Option<String>,
    refresh_token: Option<String>,
    expires_in: Option<i64>,
    error: Option<String>,
    error_description: Option<String>,
}

#[derive(Debug, Deserialize)]
struct XblResponse {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    display_claims: XblClaims,
}

#[derive(Debug, Deserialize)]
struct XblClaims {
    xui: Vec<XblUser>,
}

#[derive(Debug, Deserialize)]
struct XblUser {
    uhs: String,
}

#[derive(Debug, Deserialize)]
struct McLoginResponse {
    access_token: String,
    expires_in: i64,
}

#[derive(Debug, Deserialize)]
struct McProfileResponse {
    id: String,
    name: String,
}

#[derive(Debug, Serialize)]
struct XblProperties<'a> {
    #[serde(rename = "AuthMethod")]
    auth_method: &'a str,
    #[serde(rename = "SiteName")]
    site_name: &'a str,
    #[serde(rename = "RpsTicket")]
    rps_ticket: String,
}

#[derive(Debug, Serialize)]
struct XblRequest<'a> {
    #[serde(rename = "Properties")]
    properties: XblProperties<'a>,
    #[serde(rename = "RelyingParty")]
    relying_party: &'a str,
    #[serde(rename = "TokenType")]
    token_type: &'a str,
}

#[derive(Debug, Serialize)]
struct XstsProperties<'a> {
    #[serde(rename = "SandboxId")]
    sandbox_id: &'a str,
    #[serde(rename = "UserTokens")]
    user_tokens: Vec<&'a str>,
}

#[derive(Debug, Serialize)]
struct XstsRequest<'a> {
    #[serde(rename = "Properties")]
    properties: XstsProperties<'a>,
    #[serde(rename = "RelyingParty")]
    relying_party: &'a str,
    #[serde(rename = "TokenType")]
    token_type: &'a str,
}

#[derive(Debug, Serialize)]
struct McLoginRequest {
    #[serde(rename = "identityToken")]
    identity_token: String,
}

fn now_unix() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64
}

pub async fn start_login() -> Result<AuthStart> {
    Ok(AuthStart {
        auth_url: login_url(),
    })
}

pub fn login_url() -> String {
    let auth_url = format!(
        "https://login.live.com/oauth20_authorize.srf?client_id={}&response_type=code&redirect_uri={}&scope={}&prompt=select_account",
        urlencoding::encode(CLIENT_ID),
        urlencoding::encode(REDIRECT_URI),
        urlencoding::encode(SCOPE),
    );

    auth_url
}

pub async fn complete_login(redirect_or_code: String) -> Result<PublicAccount> {
    let code = extract_authorization_code(&redirect_or_code)?;
    let client = reqwest::Client::new();

    let token = live_token_request(
        &client,
        &[
            ("client_id", CLIENT_ID),
            ("grant_type", "authorization_code"),
            ("code", &code),
            ("redirect_uri", REDIRECT_URI),
            ("scope", SCOPE),
        ],
    )
    .await?;

    let ms_access = token
        .access_token
        .context("Microsoft token response missing access token")?;
    let refresh = token
        .refresh_token
        .context("Microsoft token response missing refresh token")?;
    let account = minecraft_login(
        &client,
        &ms_access,
        refresh,
        token.expires_in.unwrap_or(3600),
    )
    .await?;
    save_account(&account).await?;
    Ok(PublicAccount {
        profile: account.profile,
    })
}

pub async fn load_account() -> Result<Option<PublicAccount>> {
    match read_account().await? {
        Some(account) => Ok(Some(PublicAccount {
            profile: account.profile,
        })),
        None => Ok(None),
    }
}

pub async fn sign_out() -> Result<()> {
    let _ = fs::remove_file(paths::auth_path()?).await;
    let _ = Entry::new(KEYRING_SERVICE, KEYRING_USER)?.delete_credential();
    Ok(())
}

pub async fn launch_account() -> Result<Account> {
    let account = read_account()
        .await?
        .context("Sign in before launching Minecraft")?;
    if account.expires_at > now_unix() + 120 {
        return Ok(account);
    }

    refresh_account(&account.refresh_token).await
}

async fn refresh_account(refresh_token: &str) -> Result<Account> {
    let client = reqwest::Client::new();
    let token = live_token_request(
        &client,
        &[
            ("client_id", CLIENT_ID),
            ("grant_type", "refresh_token"),
            ("refresh_token", refresh_token),
            ("redirect_uri", REDIRECT_URI),
            ("scope", SCOPE),
        ],
    )
    .await?;

    let ms_access = token
        .access_token
        .context("Refresh response missing access token")?;
    let refresh = token
        .refresh_token
        .unwrap_or_else(|| refresh_token.to_string());
    let account = minecraft_login(
        &client,
        &ms_access,
        refresh,
        token.expires_in.unwrap_or(3600),
    )
    .await?;
    save_account(&account).await?;
    Ok(account)
}

async fn live_token_request(
    client: &reqwest::Client,
    form: &[(&str, &str)],
) -> Result<TokenResponse> {
    let response = client
        .post("https://login.live.com/oauth20_token.srf")
        .form(form)
        .send()
        .await?;

    let status = response.status();
    let body = response.text().await?;
    let token: TokenResponse = serde_json::from_str(&body)
        .with_context(|| format!("Microsoft token response was not JSON: {body}"))?;

    if !status.is_success() || token.error.is_some() {
        return Err(anyhow!(
            "{}",
            token
                .error_description
                .or(token.error)
                .unwrap_or_else(|| format!("Microsoft token request failed with {status}: {body}"))
        ));
    }

    Ok(token)
}

fn extract_authorization_code(input: &str) -> Result<String> {
    let trimmed = input.trim();
    if trimmed.is_empty() {
        return Err(anyhow!(
            "Paste the Microsoft redirect URL or authorization code first"
        ));
    }

    if trimmed.starts_with("http://") || trimmed.starts_with("https://") {
        let parsed = url::Url::parse(trimmed).context("The pasted redirect URL is not valid")?;
        let mut error = None;
        let mut code = None;

        for (key, value) in parsed.query_pairs() {
            match key.as_ref() {
                "error" => error = Some(value.to_string()),
                "error_description" => return Err(anyhow!("{}", value)),
                "code" => code = Some(value.to_string()),
                _ => {}
            }
        }

        if let Some(error) = error {
            return Err(anyhow!("Microsoft login failed: {error}"));
        }

        return code.context("The pasted redirect URL does not contain a code parameter");
    }

    Ok(trimmed.to_string())
}

async fn minecraft_login(
    client: &reqwest::Client,
    microsoft_access_token: &str,
    refresh_token: String,
    _ms_expires_in: i64,
) -> Result<Account> {
    let xbl: XblResponse = client
        .post("https://user.auth.xboxlive.com/user/authenticate")
        .json(&XblRequest {
            properties: XblProperties {
                auth_method: "RPS",
                site_name: "user.auth.xboxlive.com",
                rps_ticket: format!("d={microsoft_access_token}"),
            },
            relying_party: "http://auth.xboxlive.com",
            token_type: "JWT",
        })
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;

    let xsts: XblResponse = client
        .post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .json(&XstsRequest {
            properties: XstsProperties {
                sandbox_id: "RETAIL",
                user_tokens: vec![&xbl.token],
            },
            relying_party: "rp://api.minecraftservices.com/",
            token_type: "JWT",
        })
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;

    let uhs = xsts
        .display_claims
        .xui
        .first()
        .context("Xbox response did not include a user hash")?
        .uhs
        .clone();

    let mc: McLoginResponse = client
        .post("https://api.minecraftservices.com/authentication/login_with_xbox")
        .json(&McLoginRequest {
            identity_token: format!("XBL3.0 x={uhs};{}", xsts.token),
        })
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;

    let profile_response: McProfileResponse = client
        .get("https://api.minecraftservices.com/minecraft/profile")
        .bearer_auth(&mc.access_token)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await
        .context("This Microsoft account does not appear to own Minecraft Java Edition")?;

    Ok(Account {
        access_token: mc.access_token,
        refresh_token,
        expires_at: now_unix() + mc.expires_in,
        profile: MinecraftProfile {
            id: profile_response.id,
            name: profile_response.name,
        },
    })
}

async fn save_account(account: &Account) -> Result<()> {
    fs::create_dir_all(paths::app_dir()?).await?;
    Entry::new(KEYRING_SERVICE, KEYRING_USER)?.set_password(&account.refresh_token)?;

    let mut public_copy = account.clone();
    public_copy.refresh_token.clear();
    fs::write(
        paths::auth_path()?,
        serde_json::to_vec_pretty(&public_copy)?,
    )
    .await?;
    Ok(())
}

async fn read_account() -> Result<Option<Account>> {
    let path = paths::auth_path()?;
    if !path.exists() {
        return Ok(None);
    }

    let mut account: Account = serde_json::from_slice(&fs::read(path).await?)?;
    if account.refresh_token.is_empty() {
        account.refresh_token = Entry::new(KEYRING_SERVICE, KEYRING_USER)?
            .get_password()
            .context("Saved account exists, but no refresh token was found in the OS keychain")?;
    }

    Ok(Some(account))
}
