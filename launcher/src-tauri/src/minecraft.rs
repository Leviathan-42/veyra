use crate::{
    auth, paths,
    types::{LaunchResult, VersionChoice},
};
use anyhow::{anyhow, Context, Result};
use futures_util::StreamExt;
use serde::Deserialize;
use sha1::{Digest, Sha1};
use std::{
    collections::HashMap,
    fs::File,
    io::Cursor,
    path::{Path, PathBuf},
    process::Stdio,
};
use tauri::{AppHandle, Emitter};
use tokio::{
    fs,
    io::{AsyncBufReadExt, AsyncWriteExt, BufReader},
    process::Command,
};

const VERSION_MANIFEST: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const MODRINTH_API: &str = "https://api.modrinth.com/v2";
const MANAGED_MODS: &[ManagedMod] = &[
    ManagedMod {
        slug: "fabric-api",
        label: "Fabric API",
    },
    ManagedMod {
        slug: "sodium",
        label: "Sodium",
    },
    ManagedMod {
        slug: "lithium",
        label: "Lithium",
    },
    ManagedMod {
        slug: "ferrite-core",
        label: "FerriteCore",
    },
    ManagedMod {
        slug: "entityculling",
        label: "EntityCulling",
    },
    ManagedMod {
        slug: "immediatelyfast",
        label: "ImmediatelyFast",
    },
    ManagedMod {
        slug: "iris",
        label: "Iris Shaders",
    },
];

struct ManagedMod {
    slug: &'static str,
    label: &'static str,
}

#[derive(Debug, Deserialize)]
struct VersionManifest {
    versions: Vec<ManifestVersion>,
}

#[derive(Debug, Deserialize)]
struct ManifestVersion {
    id: String,
    #[serde(rename = "type")]
    version_type: String,
    url: String,
}

#[derive(Debug, Deserialize)]
struct VersionJson {
    id: String,
    #[serde(rename = "mainClass")]
    main_class: String,
    #[serde(rename = "assetIndex")]
    asset_index: AssetIndex,
    downloads: Downloads,
    libraries: Vec<Library>,
    arguments: Option<Arguments>,
}

#[derive(Debug, Deserialize)]
struct Downloads {
    client: Artifact,
}

#[derive(Debug, Deserialize)]
struct AssetIndex {
    id: String,
    url: String,
}

#[derive(Debug, Deserialize)]
struct Arguments {
    game: Vec<serde_json::Value>,
    jvm: Vec<serde_json::Value>,
}

#[derive(Debug, Deserialize)]
struct Library {
    name: String,
    downloads: Option<LibraryDownloads>,
    rules: Option<Vec<Rule>>,
    natives: Option<HashMap<String, String>>,
    extract: Option<ExtractRule>,
}

#[derive(Debug, Deserialize)]
struct LibraryDownloads {
    artifact: Option<Artifact>,
    classifiers: Option<HashMap<String, Artifact>>,
}

#[derive(Debug, Deserialize, Clone)]
struct Artifact {
    path: Option<String>,
    sha1: Option<String>,
    size: Option<u64>,
    url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct Rule {
    action: String,
    os: Option<OsRule>,
    features: Option<HashMap<String, bool>>,
}

#[derive(Debug, Deserialize)]
struct OsRule {
    name: Option<String>,
}

#[derive(Debug, Deserialize)]
struct ExtractRule {
    exclude: Option<Vec<String>>,
}

#[derive(Debug, Deserialize)]
struct AssetObjects {
    objects: HashMap<String, AssetObject>,
}

#[derive(Debug, Deserialize)]
struct AssetObject {
    hash: String,
    size: u64,
}

#[derive(Debug, Deserialize)]
struct ModrinthVersion {
    files: Vec<ModrinthFile>,
}

#[derive(Debug, Deserialize)]
struct ModrinthFile {
    url: String,
    filename: String,
    hashes: HashMap<String, String>,
    size: u64,
    primary: bool,
}

#[derive(Debug, Deserialize)]
struct FabricLoaderVersion {
    loader: FabricMavenVersion,
    intermediary: FabricMavenVersion,
    #[serde(rename = "launcherMeta")]
    launcher_meta: FabricLauncherMeta,
}

#[derive(Debug, Deserialize)]
struct FabricMavenVersion {
    maven: String,
    stable: bool,
}

#[derive(Debug, Deserialize)]
struct FabricLauncherMeta {
    libraries: FabricLibraries,
    #[serde(rename = "mainClass")]
    main_class: FabricMainClassValue,
}

#[derive(Debug, Deserialize)]
struct FabricLibraries {
    client: Vec<FabricLibrary>,
    common: Vec<FabricLibrary>,
}

#[derive(Debug, Clone, Deserialize)]
struct FabricLibrary {
    name: String,
    url: Option<String>,
    sha1: Option<String>,
    size: Option<u64>,
}

#[derive(Debug, Deserialize)]
struct FabricMainClass {
    client: String,
}

#[derive(Debug, Deserialize)]
#[serde(untagged)]
enum FabricMainClassValue {
    Modern(FabricMainClass),
    Legacy(String),
}

impl FabricMainClassValue {
    fn client(&self) -> &str {
        match self {
            FabricMainClassValue::Modern(main_class) => &main_class.client,
            FabricMainClassValue::Legacy(main_class) => main_class,
        }
    }
}

struct FabricRuntime {
    main_class: String,
    classpath: Vec<PathBuf>,
}

fn current_os_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

fn classpath_sep() -> &'static str {
    if cfg!(target_os = "windows") {
        ";"
    } else {
        ":"
    }
}

fn rule_matches(rule: &Rule) -> bool {
    let os_matches = match &rule.os {
        None => true,
        Some(os) => os
            .name
            .as_deref()
            .is_none_or(|name| name == current_os_name()),
    };

    os_matches && features_match(&rule.features)
}

fn features_match(features: &Option<HashMap<String, bool>>) -> bool {
    let Some(features) = features else {
        return true;
    };

    features
        .iter()
        .all(|(name, expected)| launcher_feature_enabled(name) == *expected)
}

fn launcher_feature_enabled(name: &str) -> bool {
    match name {
        "is_demo_user" => false,
        "has_custom_resolution" => false,
        "has_quick_plays_support" => false,
        "is_quick_play_singleplayer" => false,
        "is_quick_play_multiplayer" => false,
        "is_quick_play_realms" => false,
        _ => false,
    }
}

fn allowed(rules: &Option<Vec<Rule>>) -> bool {
    let Some(rules) = rules else {
        return true;
    };

    let mut allowed = false;
    for rule in rules {
        if rule_matches(rule) {
            allowed = rule.action == "allow";
        }
    }
    allowed
}

fn native_classifier(lib: &Library) -> Option<String> {
    lib.natives
        .as_ref()?
        .get(current_os_name())?
        .replace(
            "${arch}",
            if cfg!(target_pointer_width = "64") {
                "64"
            } else {
                "32"
            },
        )
        .into()
}

pub async fn list_versions() -> Result<Vec<VersionChoice>> {
    let manifest: VersionManifest = reqwest::get(VERSION_MANIFEST)
        .await?
        .error_for_status()?
        .json()
        .await?;
    Ok(manifest
        .versions
        .into_iter()
        .take(80)
        .map(|v| VersionChoice {
            id: v.id,
            version_type: v.version_type,
        })
        .collect())
}

pub async fn install_and_launch(
    app: AppHandle,
    java_path: String,
    version_id: String,
    quick_play_server: Option<String>,
) -> Result<LaunchResult> {
    let account = auth::launch_account().await?;
    let game_dir = paths::game_dir()?;
    fs::create_dir_all(&game_dir).await?;
    fs::create_dir_all(game_dir.join("mods")).await?;
    sync_development_block_tracker_mod(&app, &game_dir).await?;
    install_managed_client_mods(&app, &game_dir, &version_id).await?;

    app.emit("launcher-status", format!("Installing {version_id}"))
        .ok();
    let mut version = install_version(&app, &game_dir, &version_id).await?;
    let fabric = install_fabric(&app, &game_dir, &version_id).await?;
    version.main_class = fabric.main_class;

    app.emit("launcher-status", "Preparing launch arguments")
        .ok();
    let classpath = build_classpath(&game_dir, &version, &fabric.classpath)?;
    let natives_dir = game_dir.join("versions").join(&version.id).join("natives");
    fs::create_dir_all(&natives_dir).await?;
    extract_natives(&game_dir, &natives_dir, &version).await?;

    let mut args = Vec::new();
    args.extend(expand_jvm_args(
        &version,
        &game_dir,
        &classpath,
        &natives_dir,
    ));
    args.push(version.main_class.clone());
    args.extend(expand_game_args(
        &version,
        &game_dir,
        &account.profile.name,
        &account.profile.id,
        &account.access_token,
        quick_play_server.as_deref(),
    ));

    app.emit("launcher-status", "Starting Minecraft").ok();
    let child = Command::new(java_path)
        .args(args)
        .current_dir(&game_dir)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .context("Failed to spawn Java. Check that Java 21+ is installed and on PATH.")?;

    let pid = child.id().unwrap_or_default();
    stream_child_output(&app, child, pid);

    Ok(LaunchResult { pid })
}

async fn install_managed_client_mods(
    app: &AppHandle,
    game_dir: &Path,
    version_id: &str,
) -> Result<()> {
    let mods_dir = game_dir.join("mods");
    fs::create_dir_all(&mods_dir).await?;
    remove_old_managed_client_mods(&mods_dir).await?;

    for managed in MANAGED_MODS {
        if let Err(error) = install_modrinth_mod(app, &mods_dir, managed, version_id).await {
            app.emit(
                "launch-log",
                format!("Skipped {} for {version_id}: {error}", managed.label),
            )
            .ok();
        }
    }

    Ok(())
}

async fn remove_old_managed_client_mods(mods_dir: &Path) -> Result<()> {
    let mut entries = fs::read_dir(mods_dir).await?;
    while let Some(entry) = entries.next_entry().await? {
        let path = entry.path();
        if path.extension().and_then(|ext| ext.to_str()) != Some("jar") {
            continue;
        }

        let file_name = entry.file_name().to_string_lossy().to_lowercase();
        let managed = file_name.starts_with("fabric-api-")
            || file_name.starts_with("sodium-fabric-")
            || file_name.starts_with("lithium-fabric-")
            || file_name.starts_with("iris-fabric-")
            || file_name.starts_with("ferritecore-")
            || file_name.starts_with("entityculling-fabric-")
            || file_name.starts_with("immediatelyfast-fabric-");

        if managed {
            fs::remove_file(path).await?;
        }
    }

    Ok(())
}

async fn install_modrinth_mod(
    app: &AppHandle,
    mods_dir: &Path,
    managed: &ManagedMod,
    version_id: &str,
) -> Result<()> {
    let client = reqwest::Client::new();
    let url = format!(
        "{MODRINTH_API}/project/{}/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22{}%22%5D",
        managed.slug, version_id
    );
    let versions: Vec<ModrinthVersion> = client
        .get(url)
        .header(reqwest::header::USER_AGENT, "VeyraLauncher")
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    let version = versions
        .first()
        .ok_or_else(|| anyhow!("no compatible Fabric build found"))?;
    let file = version
        .files
        .iter()
        .find(|file| file.primary)
        .or_else(|| version.files.first())
        .ok_or_else(|| anyhow!("Modrinth version has no files"))?;
    let destination = mods_dir.join(&file.filename);

    download_url(
        app,
        managed.label,
        &file.url,
        &destination,
        file.hashes.get("sha1").map(String::as_str),
        Some(file.size),
    )
    .await?;

    app.emit(
        "launch-log",
        format!("Installed {} ({})", managed.label, file.filename),
    )
    .ok();

    Ok(())
}

async fn sync_development_block_tracker_mod(app: &AppHandle, game_dir: &Path) -> Result<()> {
    let manifest_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let Some(workspace_dir) = manifest_dir.parent().and_then(Path::parent) else {
        return Ok(());
    };

    let source = workspace_dir
        .join("mod")
        .join("build")
        .join("libs")
        .join("block-tracker-0.1.0.jar");

    if !source.exists() {
        app.emit(
            "launch-log",
            format!(
                "Veyra mod jar was not found at {}. Build the mod to enable in-game utilities.",
                source.display()
            ),
        )
        .ok();
        return Ok(());
    }

    let mods_dir = game_dir.join("mods");
    fs::create_dir_all(&mods_dir).await?;

    let mut entries = fs::read_dir(&mods_dir).await?;
    while let Some(entry) = entries.next_entry().await? {
        let path = entry.path();
        let file_name = entry.file_name().to_string_lossy().to_string();
        let is_block_tracker = file_name.starts_with("block-tracker-")
            && path.extension().and_then(|ext| ext.to_str()) == Some("jar");

        if is_block_tracker {
            fs::remove_file(path).await?;
        }
    }

    let destination = mods_dir.join(
        source
            .file_name()
            .context("Veyra mod jar is missing a file name")?,
    );
    fs::copy(&source, &destination).await?;

    app.emit(
        "launch-log",
        format!("Synced Veyra mod to {}", destination.display()),
    )
    .ok();

    Ok(())
}

fn stream_child_output(app: &AppHandle, mut child: tokio::process::Child, pid: u32) {
    if let Some(stdout) = child.stdout.take() {
        let app = app.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(stdout).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                app.emit("launch-log", line).ok();
            }
        });
    }

    if let Some(stderr) = child.stderr.take() {
        let app = app.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(stderr).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                app.emit("launch-log", line).ok();
            }
        });
    }

    let app = app.clone();
    tokio::spawn(async move {
        match child.wait().await {
            Ok(status) => {
                let code = status
                    .code()
                    .map(|code| code.to_string())
                    .unwrap_or_else(|| "terminated".to_string());
                app.emit(
                    "launcher-status",
                    format!("Minecraft process {pid} exited with code {code}"),
                )
                .ok();
            }
            Err(error) => {
                app.emit(
                    "launcher-status",
                    format!("Failed to wait for Minecraft process {pid}: {error}"),
                )
                .ok();
            }
        }
    });
}

async fn install_version(
    app: &AppHandle,
    game_dir: &Path,
    version_id: &str,
) -> Result<VersionJson> {
    let manifest: VersionManifest = reqwest::get(VERSION_MANIFEST)
        .await?
        .error_for_status()?
        .json()
        .await?;
    let selected = manifest
        .versions
        .into_iter()
        .find(|version| version.id == version_id)
        .ok_or_else(|| anyhow!("Version not found: {version_id}"))?;

    let version: VersionJson = reqwest::get(&selected.url)
        .await?
        .error_for_status()?
        .json()
        .await?;
    let version_dir = game_dir.join("versions").join(&version.id);
    fs::create_dir_all(&version_dir).await?;

    download_artifact(
        app,
        "client jar",
        &version.downloads.client,
        &version_dir.join(format!("{}.jar", version.id)),
    )
    .await?;
    download_url(
        app,
        "version json",
        &selected.url,
        &version_dir.join(format!("{}.json", version.id)),
        None,
        None,
    )
    .await?;

    install_libraries(app, game_dir, &version).await?;
    install_assets(app, game_dir, &version).await?;

    Ok(version)
}

async fn install_libraries(app: &AppHandle, game_dir: &Path, version: &VersionJson) -> Result<()> {
    for library in &version.libraries {
        if !allowed(&library.rules) {
            continue;
        }

        if let Some(artifact) = library.downloads.as_ref().and_then(|d| d.artifact.as_ref()) {
            let path = artifact
                .path
                .as_deref()
                .context("Library artifact missing path")?;
            download_artifact(
                app,
                &library.name,
                artifact,
                &game_dir.join("libraries").join(path),
            )
            .await?;
        }

        if let Some(classifier) = native_classifier(library) {
            if let Some(native) = library
                .downloads
                .as_ref()
                .and_then(|d| d.classifiers.as_ref())
                .and_then(|c| c.get(&classifier))
            {
                let path = native
                    .path
                    .as_deref()
                    .context("Native artifact missing path")?;
                download_artifact(
                    app,
                    &format!("{} native", library.name),
                    native,
                    &game_dir.join("libraries").join(path),
                )
                .await?;
            }
        }
    }

    Ok(())
}

async fn install_assets(app: &AppHandle, game_dir: &Path, version: &VersionJson) -> Result<()> {
    let index_path = game_dir
        .join("assets")
        .join("indexes")
        .join(format!("{}.json", version.asset_index.id));
    download_url(
        app,
        "asset index",
        &version.asset_index.url,
        &index_path,
        None,
        None,
    )
    .await?;

    let assets: AssetObjects = serde_json::from_slice(&fs::read(&index_path).await?)?;
    let total = assets.objects.len();
    for (index, object) in assets.objects.values().enumerate() {
        let prefix = &object.hash[0..2];
        let object_path = game_dir
            .join("assets")
            .join("objects")
            .join(prefix)
            .join(&object.hash);
        let url = format!(
            "https://resources.download.minecraft.net/{prefix}/{}",
            object.hash
        );
        download_url(
            app,
            &format!("asset {}/{}", index + 1, total),
            &url,
            &object_path,
            Some(&object.hash),
            Some(object.size),
        )
        .await?;
    }

    Ok(())
}

async fn install_fabric(
    app: &AppHandle,
    game_dir: &Path,
    version_id: &str,
) -> Result<FabricRuntime> {
    app.emit(
        "launcher-status",
        format!("Resolving Fabric Loader for {version_id}"),
    )
    .ok();

    let url = format!("https://meta.fabricmc.net/v2/versions/loader/{version_id}");
    let versions: Vec<FabricLoaderVersion> =
        reqwest::get(&url).await?.error_for_status()?.json().await?;
    let selected = versions
        .iter()
        .find(|version| version.loader.stable)
        .or_else(|| versions.first())
        .ok_or_else(|| anyhow!("No Fabric Loader build is available for {version_id}"))?;

    let mut libs = Vec::new();
    libs.push(FabricLibrary {
        name: selected.loader.maven.clone(),
        url: Some("https://maven.fabricmc.net/".to_string()),
        sha1: None,
        size: None,
    });
    libs.push(FabricLibrary {
        name: selected.intermediary.maven.clone(),
        url: Some("https://maven.fabricmc.net/".to_string()),
        sha1: None,
        size: None,
    });
    libs.extend(selected.launcher_meta.libraries.common.iter().cloned());
    libs.extend(selected.launcher_meta.libraries.client.iter().cloned());

    let mut classpath = Vec::new();
    for lib in libs {
        let rel = maven_path(&lib.name)?;
        let base_url = lib.url.as_deref().unwrap_or("https://maven.fabricmc.net/");
        let url = format!("{}{}", base_url.trim_end_matches('/'), format!("/{rel}"));
        let path = game_dir.join("libraries").join(&rel);
        download_url(
            app,
            &format!("Fabric {}", lib.name),
            &url,
            &path,
            lib.sha1.as_deref(),
            lib.size,
        )
        .await?;
        classpath.push(path);
    }

    Ok(FabricRuntime {
        main_class: selected.launcher_meta.main_class.client().to_string(),
        classpath,
    })
}

fn maven_path(coordinate: &str) -> Result<String> {
    let parts: Vec<&str> = coordinate.split(':').collect();
    if parts.len() < 3 {
        return Err(anyhow!("Invalid Maven coordinate: {coordinate}"));
    }

    let group = parts[0].replace('.', "/");
    let artifact = parts[1];
    let version = parts[2];
    let classifier = parts.get(3).copied();
    let file_name = match classifier {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.jar"),
        None => format!("{artifact}-{version}.jar"),
    };

    Ok(format!("{group}/{artifact}/{version}/{file_name}"))
}

async fn download_artifact(
    app: &AppHandle,
    label: &str,
    artifact: &Artifact,
    path: &Path,
) -> Result<()> {
    let url = match artifact.url.as_deref() {
        Some(url) => url.to_string(),
        None => {
            let path = artifact
                .path
                .as_deref()
                .with_context(|| format!("Artifact {label} is missing a download URL and path"))?;
            format!("https://libraries.minecraft.net/{path}")
        }
    };

    download_url(
        app,
        label,
        &url,
        path,
        artifact.sha1.as_deref(),
        artifact.size,
    )
    .await
}

async fn download_url(
    app: &AppHandle,
    label: &str,
    url: &str,
    path: &Path,
    sha1: Option<&str>,
    size: Option<u64>,
) -> Result<()> {
    if valid_existing(path, sha1, size).await? {
        return Ok(());
    }

    let mut last_error = None;
    for attempt in 1..=5 {
        match download_url_once(app, label, url, path, sha1, size, attempt).await {
            Ok(()) => return Ok(()),
            Err(error) => {
                last_error = Some(error);
                tokio::time::sleep(std::time::Duration::from_millis(350 * attempt)).await;
            }
        }
    }

    Err(last_error.unwrap_or_else(|| anyhow!("Download failed: {label}")))
}

async fn download_url_once(
    app: &AppHandle,
    label: &str,
    url: &str,
    path: &Path,
    sha1: Option<&str>,
    size: Option<u64>,
    attempt: u64,
) -> Result<()> {
    if valid_existing(path, sha1, size).await? {
        return Ok(());
    }

    if path.exists() {
        let _ = fs::remove_file(path).await;
    }

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).await?;
    }

    let part_path = partial_path(path)?;
    let mut resume_from = match fs::metadata(&part_path).await {
        Ok(metadata) => metadata.len(),
        Err(_) => 0,
    };

    if let Some(expected_size) = size {
        if resume_from > expected_size {
            let _ = fs::remove_file(&part_path).await;
            resume_from = 0;
        }
    }

    let resume_text = if resume_from > 0 {
        format!("; resuming at {} KB", resume_from / 1024)
    } else {
        String::new()
    };
    app.emit(
        "launcher-status",
        format!("Downloading {label} (try {attempt}/5{resume_text})"),
    )
    .ok();

    let client = reqwest::Client::new();
    let mut request = client.get(url);
    if resume_from > 0 {
        request = request.header(reqwest::header::RANGE, format!("bytes={resume_from}-"));
    }

    let response = request.send().await?;
    let status = response.status();
    if resume_from > 0 && status == reqwest::StatusCode::OK {
        let _ = fs::remove_file(&part_path).await;
        resume_from = 0;
    } else if resume_from > 0 && status != reqwest::StatusCode::PARTIAL_CONTENT {
        let _ = fs::remove_file(&part_path).await;
        resume_from = 0;
    }

    let response = if resume_from == 0 && status != reqwest::StatusCode::OK {
        response.error_for_status()?
    } else if resume_from > 0 && status != reqwest::StatusCode::PARTIAL_CONTENT {
        client.get(url).send().await?.error_for_status()?
    } else {
        response.error_for_status()?
    };

    let mut stream = response.bytes_stream();
    let mut file = if resume_from > 0 {
        fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(&part_path)
            .await?
    } else {
        fs::File::create(&part_path).await?
    };

    while let Some(chunk) = stream.next().await {
        file.write_all(&chunk?).await?;
    }
    file.flush().await?;
    drop(file);

    fs::rename(&part_path, path).await?;

    if !valid_existing(path, sha1, size).await? {
        let _ = fs::remove_file(path).await;
        return Err(anyhow!(
            "Downloaded file failed validation: {}",
            path.display()
        ));
    }

    Ok(())
}

fn partial_path(path: &Path) -> Result<PathBuf> {
    let file_name = path
        .file_name()
        .context("Download path is missing a file name")?
        .to_string_lossy();
    Ok(path.with_file_name(format!("{file_name}.part")))
}

async fn valid_existing(
    path: &Path,
    expected_sha1: Option<&str>,
    expected_size: Option<u64>,
) -> Result<bool> {
    if !path.exists() {
        return Ok(false);
    }

    if let Some(size) = expected_size {
        if fs::metadata(path).await?.len() != size {
            return Ok(false);
        }
    }

    if let Some(expected) = expected_sha1 {
        let actual = hex_sha1(&fs::read(path).await?);
        if actual != expected {
            return Ok(false);
        }
    }

    Ok(true)
}

fn hex_sha1(bytes: &[u8]) -> String {
    let mut hasher = Sha1::new();
    hasher.update(bytes);
    format!("{:x}", hasher.finalize())
}

fn build_classpath(game_dir: &Path, version: &VersionJson, extra: &[PathBuf]) -> Result<String> {
    let mut entries = Vec::new();

    for path in extra {
        entries.push(path.display().to_string());
    }

    for library in &version.libraries {
        if !allowed(&library.rules) {
            continue;
        }

        if let Some(path) = library
            .downloads
            .as_ref()
            .and_then(|d| d.artifact.as_ref())
            .and_then(|a| a.path.as_ref())
        {
            entries.push(game_dir.join("libraries").join(path).display().to_string());
        }
    }

    entries.push(
        game_dir
            .join("versions")
            .join(&version.id)
            .join(format!("{}.jar", version.id))
            .display()
            .to_string(),
    );

    Ok(entries.join(classpath_sep()))
}

async fn extract_natives(game_dir: &Path, natives_dir: &Path, version: &VersionJson) -> Result<()> {
    for library in &version.libraries {
        if !allowed(&library.rules) {
            continue;
        }

        let Some(classifier) = native_classifier(library) else {
            continue;
        };

        let Some(artifact) = library
            .downloads
            .as_ref()
            .and_then(|d| d.classifiers.as_ref())
            .and_then(|c| c.get(&classifier))
        else {
            continue;
        };

        let path = artifact
            .path
            .as_deref()
            .context("Native artifact missing path")?;
        let bytes = fs::read(game_dir.join("libraries").join(path)).await?;
        let excludes = library
            .extract
            .as_ref()
            .and_then(|e| e.exclude.as_ref())
            .cloned()
            .unwrap_or_default();

        let mut archive = zip::ZipArchive::new(Cursor::new(bytes))?;
        for i in 0..archive.len() {
            let mut file = archive.by_index(i)?;
            let name = file.name().replace('\\', "/");
            if file.is_dir() || excludes.iter().any(|exclude| name.starts_with(exclude)) {
                continue;
            }

            let output = natives_dir.join(&name);
            if let Some(parent) = output.parent() {
                std::fs::create_dir_all(parent)?;
            }

            let mut out = File::create(output)?;
            std::io::copy(&mut file, &mut out)?;
        }
    }

    Ok(())
}

fn expand_jvm_args(
    version: &VersionJson,
    game_dir: &Path,
    classpath: &str,
    natives_dir: &Path,
) -> Vec<String> {
    let mut args = vec![
        "-Xmx4G".to_string(),
        "-XX:+UnlockExperimentalVMOptions".to_string(),
        "-XX:+UseG1GC".to_string(),
    ];

    if let Some(arguments) = &version.arguments {
        for value in &arguments.jvm {
            append_argument(value, &mut args, |raw| {
                replace_common(raw, version, game_dir, classpath, natives_dir)
            });
        }
    } else {
        args.push(format!("-Djava.library.path={}", natives_dir.display()));
        args.push("-cp".to_string());
        args.push(classpath.to_string());
    }

    args
}

fn expand_game_args(
    version: &VersionJson,
    game_dir: &Path,
    username: &str,
    uuid: &str,
    access_token: &str,
    quick_play_server: Option<&str>,
) -> Vec<String> {
    let mut args = Vec::new();

    if let Some(arguments) = &version.arguments {
        for value in &arguments.game {
            append_argument(value, &mut args, |raw| {
                replace_game(raw, version, game_dir, username, uuid, access_token)
            });
        }
    } else {
        args.extend([
            "--username".into(),
            username.into(),
            "--uuid".into(),
            uuid.into(),
            "--accessToken".into(),
            access_token.into(),
            "--version".into(),
            version.id.clone(),
            "--gameDir".into(),
            game_dir.display().to_string(),
            "--assetsDir".into(),
            game_dir.join("assets").display().to_string(),
            "--assetIndex".into(),
            version.asset_index.id.clone(),
            "--userType".into(),
            "msa".into(),
            "--versionType".into(),
            "snapshot".into(),
        ]);
    }

    if let Some(server) = quick_play_server.filter(|server| !server.trim().is_empty()) {
        args.push("--quickPlayMultiplayer".into());
        args.push(server.trim().into());
    }

    args
}

fn append_argument<F>(value: &serde_json::Value, args: &mut Vec<String>, replace: F)
where
    F: Fn(&str) -> String + Copy,
{
    if let Some(raw) = value.as_str() {
        args.push(replace(raw));
        return;
    }

    let Some(obj) = value.as_object() else {
        return;
    };

    if let Some(rules) = obj
        .get("rules")
        .and_then(|v| serde_json::from_value::<Vec<Rule>>(v.clone()).ok())
    {
        if !allowed(&Some(rules)) {
            return;
        }
    }

    match obj.get("value") {
        Some(serde_json::Value::String(raw)) => args.push(replace(raw)),
        Some(serde_json::Value::Array(values)) => {
            for item in values {
                if let Some(raw) = item.as_str() {
                    args.push(replace(raw));
                }
            }
        }
        _ => {}
    }
}

fn replace_common(
    raw: &str,
    version: &VersionJson,
    game_dir: &Path,
    classpath: &str,
    natives_dir: &Path,
) -> String {
    raw.replace("${natives_directory}", &natives_dir.display().to_string())
        .replace("${launcher_name}", "VeyraLauncher")
        .replace("${launcher_version}", env!("CARGO_PKG_VERSION"))
        .replace("${classpath}", classpath)
        .replace("${version_name}", &version.id)
        .replace(
            "${library_directory}",
            &game_dir.join("libraries").display().to_string(),
        )
        .replace("${classpath_separator}", classpath_sep())
}

fn replace_game(
    raw: &str,
    version: &VersionJson,
    game_dir: &Path,
    username: &str,
    uuid: &str,
    access_token: &str,
) -> String {
    raw.replace("${auth_player_name}", username)
        .replace("${version_name}", &version.id)
        .replace("${game_directory}", &game_dir.display().to_string())
        .replace(
            "${assets_root}",
            &game_dir.join("assets").display().to_string(),
        )
        .replace("${assets_index_name}", &version.asset_index.id)
        .replace("${auth_uuid}", uuid)
        .replace("${auth_access_token}", access_token)
        .replace("${clientid}", "")
        .replace("${auth_xuid}", "")
        .replace("${user_type}", "msa")
        .replace("${version_type}", "snapshot")
}
