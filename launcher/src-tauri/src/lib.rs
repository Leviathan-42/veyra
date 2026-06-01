mod auth;
mod error;
mod minecraft;
mod paths;
mod types;

use crate::types::{
    AuthCodePayload, AuthStart, InstalledMod, LaunchResult, PublicAccount, VersionChoice,
};
use std::{env, process::Command};
use tauri::{Emitter, Manager, WebviewUrl, WebviewWindowBuilder};

#[tauri::command]
async fn auth_start_login() -> Result<AuthStart, String> {
    auth::start_login().await.map_err(error::stringify)
}

#[tauri::command]
async fn auth_open_login_window(app: tauri::AppHandle) -> Result<(), String> {
    if let Some(existing) = app.get_webview_window("microsoft-auth") {
        existing.set_focus().map_err(|error| error.to_string())?;
        return Ok(());
    }

    let auth_url = auth::login_url()
        .parse()
        .map_err(|error| format!("Invalid Microsoft auth URL: {error}"))?;
    let app_for_nav = app.clone();

    WebviewWindowBuilder::new(&app, "microsoft-auth", WebviewUrl::External(auth_url))
        .title("Microsoft Sign In")
        .inner_size(520.0, 720.0)
        .resizable(true)
        .center()
        .on_navigation(move |url| {
            if url.scheme() == "https"
                && url.host_str() == Some("login.live.com")
                && url.path() == "/oauth20_desktop.srf"
            {
                let mut code = None;
                let mut error = None;

                for (key, value) in url.query_pairs() {
                    match key.as_ref() {
                        "code" => code = Some(value.to_string()),
                        "error_description" => error = Some(value.to_string()),
                        "error" => error = Some(value.to_string()),
                        _ => {}
                    }
                }

                if let Some(code) = code {
                    app_for_nav.emit("auth-code", AuthCodePayload { code }).ok();
                } else if let Some(error) = error {
                    app_for_nav.emit("auth-error", error).ok();
                } else {
                    app_for_nav
                        .emit(
                            "auth-error",
                            "Microsoft redirect did not include an authorization code",
                        )
                        .ok();
                }

                if let Some(window) = app_for_nav.get_webview_window("microsoft-auth") {
                    window.close().ok();
                }

                return false;
            }

            true
        })
        .build()
        .map_err(|error| error.to_string())?;

    Ok(())
}

#[tauri::command]
async fn auth_complete_login(redirect_or_code: String) -> Result<PublicAccount, String> {
    auth::complete_login(redirect_or_code)
        .await
        .map_err(error::stringify)
}

#[tauri::command]
async fn auth_load_saved() -> Result<Option<PublicAccount>, String> {
    auth::load_account().await.map_err(error::stringify)
}

#[tauri::command]
async fn auth_debug_path() -> Result<String, String> {
    paths::auth_path()
        .map(|path| path.display().to_string())
        .map_err(error::stringify)
}

#[tauri::command]
async fn auth_sign_out() -> Result<(), String> {
    auth::sign_out().await.map_err(error::stringify)
}

#[tauri::command]
async fn list_versions() -> Result<Vec<VersionChoice>, String> {
    minecraft::list_versions().await.map_err(error::stringify)
}

#[tauri::command]
async fn install_and_launch(
    app: tauri::AppHandle,
    java_path: String,
    version_id: String,
    quick_play_server: Option<String>,
) -> Result<LaunchResult, String> {
    minecraft::install_and_launch(app, java_path, version_id, quick_play_server)
        .await
        .map_err(error::stringify)
}

#[tauri::command]
async fn mods_dir() -> Result<String, String> {
    let dir = paths::game_dir().map_err(error::stringify)?.join("mods");
    tokio::fs::create_dir_all(&dir)
        .await
        .map_err(|error| error.to_string())?;
    Ok(dir.display().to_string())
}

#[tauri::command]
async fn list_installed_mods() -> Result<Vec<InstalledMod>, String> {
    let dir = paths::game_dir().map_err(error::stringify)?.join("mods");
    tokio::fs::create_dir_all(&dir)
        .await
        .map_err(|error| error.to_string())?;

    let mut entries = tokio::fs::read_dir(dir)
        .await
        .map_err(|error| error.to_string())?;
    let mut mods = Vec::new();

    while let Some(entry) = entries
        .next_entry()
        .await
        .map_err(|error| error.to_string())?
    {
        let path = entry.path();
        if path.extension().and_then(|ext| ext.to_str()) != Some("jar") {
            continue;
        }

        let metadata = entry.metadata().await.map_err(|error| error.to_string())?;
        mods.push(InstalledMod {
            file_name: entry.file_name().to_string_lossy().to_string(),
            size_bytes: metadata.len(),
        });
    }

    mods.sort_by(|a, b| a.file_name.cmp(&b.file_name));
    Ok(mods)
}

#[tauri::command]
async fn open_mods_folder() -> Result<(), String> {
    let dir = paths::game_dir().map_err(error::stringify)?.join("mods");
    tokio::fs::create_dir_all(&dir)
        .await
        .map_err(|error| error.to_string())?;

    if cfg!(target_os = "windows") {
        Command::new("explorer")
            .arg(&dir)
            .spawn()
            .map_err(|error| error.to_string())?;
    } else if cfg!(target_os = "macos") {
        Command::new("open")
            .arg(&dir)
            .spawn()
            .map_err(|error| error.to_string())?;
    } else {
        Command::new("xdg-open")
            .arg(&dir)
            .spawn()
            .map_err(|error| error.to_string())?;
    }

    Ok(())
}

pub fn run() {
    force_linux_webview_x11();

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            auth_start_login,
            auth_open_login_window,
            auth_complete_login,
            auth_load_saved,
            auth_debug_path,
            auth_sign_out,
            list_versions,
            install_and_launch,
            mods_dir,
            list_installed_mods,
            open_mods_folder
        ])
        .run(tauri::generate_context!())
        .expect("failed to run Veyra Launcher");
}

fn force_linux_webview_x11() {
    if !cfg!(target_os = "linux") {
        return;
    }

    env::set_var("GDK_BACKEND", "x11");
    env::set_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
}
