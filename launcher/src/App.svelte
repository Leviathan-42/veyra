<script lang="ts">
  import { invoke } from '@tauri-apps/api/core';
  import { listen } from '@tauri-apps/api/event';
  import { primaryMonitor as getPrimaryMonitor } from '@tauri-apps/api/window';
  import { onMount } from 'svelte';

  type Profile = {
    id: string;
    name: string;
  };

  type Account = {
    profile: Profile;
  };

  type AuthCodePayload = {
    code: string;
  };

  type VersionChoice = {
    id: string;
    version_type: string;
  };

  type InstalledMod = {
    file_name: string;
    size_bytes: number;
  };

  type JavaRuntime = {
    path: string;
    version: number | null;
    version_string: string;
    vendor: string;
    compatible: boolean;
  };

  type RenderProfile = 'vulkan' | 'opengl';
  type NavView = 'play' | 'mods' | 'settings' | 'logs';
  type Theme = 'dark' | 'light';
  type ResolutionMode = 'auto' | 'custom';

  let account: Account | null = null;
  let versions: VersionChoice[] = [];
  let installedMods: InstalledMod[] = [];
  let activeMods: InstalledMod[] = [];
  let javaRuntimes: JavaRuntime[] = [];
  let selectedVersion = '';
  let javaPath = '';
  let renderProfile: RenderProfile = 'vulkan';
  let activeView: NavView = 'play';
  let theme: Theme = 'dark';
  let resolutionMode: ResolutionMode = 'auto';
  let windowWidth = 1920;
  let windowHeight = 1080;
  let primaryMonitorWidth = 1920;
  let primaryMonitorHeight = 1080;
  let primaryMonitorName = 'Primary monitor';
  let primaryMonitorDetected = false;
  let fullscreen = true;
  let memoryGb = 6;
  let status = 'Ready';
  let modsPath = '';
  let authPath = '';
  let launchLogs: string[] = [];
  let authBusy = false;
  let launchBusy = false;
  let booting = true;
  let bootReady = false;
  let bootLeaving = false;

  $: canLaunch = !!account && !!selectedVersion && !launchBusy;
  $: builtInMods = activeMods.filter((mod) => mod.file_name.startsWith('block-tracker-'));
  $: profileName = renderProfile === 'vulkan' ? 'VulkanMod' : 'OpenGL + Iris';
  $: effectiveWindowWidth = resolutionMode === 'auto' ? primaryMonitorWidth : windowWidth;
  $: effectiveWindowHeight = resolutionMode === 'auto' ? primaryMonitorHeight : windowHeight;
  $: pageTitle =
    activeView === 'play'
      ? 'Play'
      : activeView === 'mods'
        ? 'Mod library'
        : activeView === 'settings'
          ? 'Settings'
          : 'Launch log';
  $: pageDescription =
    activeView === 'play'
      ? 'Your configured Veyra instance'
      : activeView === 'mods'
        ? 'Manage the files attached to this render profile'
        : activeView === 'settings'
          ? 'Minecraft, Java, and display configuration'
          : 'Output from the current Minecraft process';

  onMount(async () => {
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reducedMotion) {
      booting = false;
    } else {
      window.setTimeout(() => (bootReady = true), 520);
      window.setTimeout(() => (bootLeaving = true), 1280);
      window.setTimeout(() => (booting = false), 1580);
    }

    const savedTheme = window.localStorage.getItem('veyra-theme');
    if (savedTheme === 'dark' || savedTheme === 'light') {
      theme = savedTheme;
    }

    const savedMemory = Number(window.localStorage.getItem('veyra-memory-gb'));
    if (Number.isFinite(savedMemory) && savedMemory >= 2 && savedMemory <= 16) {
      memoryGb = Math.round(savedMemory);
    }

    await detectPrimaryResolution();

    await listen<string>('launcher-status', (event) => {
      status = event.payload;
    });

    await listen<string>('launch-log', (event) => {
      launchLogs = [...launchLogs.slice(-160), event.payload];
    });

    await listen<AuthCodePayload>('auth-code', async (event) => {
      await completeLogin(event.payload.code);
    });

    await listen<string>('auth-error', (event) => {
      status = event.payload;
      authBusy = false;
    });

    try {
      authPath = await invoke<string>('auth_debug_path');
      account = await invoke<Account | null>('auth_load_saved');
    } catch (error) {
      status = String(error);
    }

    await Promise.all([loadVersions(), loadJavaRuntimes(), loadMods()]);
  });

  async function loadVersions() {
    status = 'Loading Minecraft versions';
    try {
      versions = await invoke<VersionChoice[]>('list_versions');
      selectedVersion =
        versions.find((version) => version.version_type === 'release')?.id ??
        versions[0]?.id ??
        '';
      status = 'Ready';
    } catch (error) {
      status = String(error);
    }
  }

  async function loadJavaRuntimes() {
    try {
      javaRuntimes = await invoke<JavaRuntime[]>('detect_java_runtimes');
      const best = javaRuntimes.find((runtime) => runtime.compatible) ?? javaRuntimes[0];
      if (best && !javaPath) {
        javaPath = best.path;
        status = `Detected ${best.vendor} Java ${best.version ?? '?'} runtime`;
      } else if (javaRuntimes.length === 0) {
        status = 'Java 25 will be installed automatically when Minecraft launches';
      }
    } catch (error) {
      status = `Java auto-detect failed: ${String(error)}`;
    }
  }

  async function loadMods() {
    try {
      const [profilePath, profileMods, currentActiveMods] = await Promise.all([
        invoke<string>('profile_mods_dir', { renderProfile }),
        invoke<InstalledMod[]>('list_profile_installed_mods', { renderProfile }),
        invoke<InstalledMod[]>('list_installed_mods')
      ]);
      modsPath = profilePath;
      installedMods = profileMods;
      activeMods = currentActiveMods;
    } catch (error) {
      status = String(error);
    }
  }

  async function startLogin() {
    authBusy = true;
    status = 'Opening Microsoft sign-in';

    try {
      await invoke('auth_open_login_window');
      status = 'Complete Microsoft sign-in in the launcher window';
    } catch (error) {
      status = String(error);
      authBusy = false;
    }
  }

  async function completeLogin(code: string) {
    status = 'Completing Microsoft sign-in';

    try {
      account = await invoke<Account>('auth_complete_login', {
        redirectOrCode: code
      });
      status = `Signed in as ${account.profile.name}`;
    } catch (error) {
      status = String(error);
    } finally {
      authBusy = false;
    }
  }

  async function signOut() {
    await invoke('auth_sign_out');
    account = null;
    status = 'Signed out';
  }

  function toggleTheme() {
    theme = theme === 'dark' ? 'light' : 'dark';
    window.localStorage.setItem('veyra-theme', theme);
  }

  function setMemory(value: number) {
    memoryGb = Math.max(2, Math.min(16, Math.round(value)));
    window.localStorage.setItem('veyra-memory-gb', String(memoryGb));
  }

  async function detectPrimaryResolution() {
    try {
      const monitor = await getPrimaryMonitor();
      if (!monitor) return;

      primaryMonitorWidth = monitor.size.width;
      primaryMonitorHeight = monitor.size.height;
      primaryMonitorName = monitor.name || 'Primary monitor';
      primaryMonitorDetected = true;
    } catch {
      primaryMonitorDetected = false;
    }
  }

  async function openModsFolder() {
    await invoke('open_profile_mods_folder', { renderProfile });
    await loadMods();
  }

  async function setRenderProfile(profile: RenderProfile) {
    renderProfile = profile;
    await loadMods();
  }

  async function launch() {
    launchBusy = true;
    launchLogs = [];
    status = `Installing ${selectedVersion}`;

    try {
      const result = await invoke<{ pid: number }>('install_and_launch', {
        javaPath,
        versionId: selectedVersion,
        quickPlayServer: null,
        windowWidth: effectiveWindowWidth,
        windowHeight: effectiveWindowHeight,
        fullscreen,
        renderProfile,
        memoryMb: memoryGb * 1024
      });
      status = `Minecraft started with process id ${result.pid}`;
    } catch (error) {
      status = String(error);
    } finally {
      launchBusy = false;
    }
  }

  function prettySize(bytes: number) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
</script>

<main class="app-shell" data-theme={theme}>
  {#if booting}
    <div class:ready={bootReady} class:leaving={bootLeaving} class="boot-screen" aria-label="Starting Veyra">
      <div class="boot-grid" aria-hidden="true"></div>
      <div class="boot-sequence">
        <div class="boot-orbit" aria-hidden="true"><span></span></div>
        <div class="boot-mark" aria-hidden="true"><span>V</span></div>
        <strong>VEYRA</strong>
        <small>CLIENT SYSTEM</small>
        <div class="boot-progress" aria-hidden="true"><span></span></div>
        <p><i></i>{bootReady ? 'SYSTEM READY' : 'INITIALIZING CLIENT'}</p>
      </div>
    </div>
  {/if}
  <aside class="sidebar" aria-label="Veyra navigation">
    <div class="brand">
      <div class="brand-mark" aria-hidden="true"><span>V</span></div>
      <div>
        <strong>Veyra</strong>
        <span>Launcher</span>
      </div>
    </div>

    <nav class="navigation">
      <span class="nav-heading">Launcher</span>

      <button class:active={activeView === 'play'} on:click={() => (activeView = 'play')}>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m8 5 11 7-11 7V5Z"></path>
        </svg>
        <span>Play</span>
      </button>

      <button class:active={activeView === 'mods'} on:click={() => (activeView = 'mods')}>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M8 4h8v5h4v7h-5v4H8v-4H4V9h4V4Z"></path>
        </svg>
        <span>Mod library</span>
        <small>{installedMods.length + builtInMods.length}</small>
      </button>

      <button class:active={activeView === 'settings'} on:click={() => (activeView = 'settings')}>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="3"></circle>
          <path d="M19.4 15a1.8 1.8 0 0 0 .4 2l.1.1-2.8 2.8-.1-.1a1.8 1.8 0 0 0-2-.4 1.8 1.8 0 0 0-1.1 1.7v.2H10v-.2A1.8 1.8 0 0 0 8.9 19a1.8 1.8 0 0 0-2 .4l-.1.1L4 16.7l.1-.1a1.8 1.8 0 0 0 .4-2 1.8 1.8 0 0 0-1.7-1.1h-.2v-4h.2a1.8 1.8 0 0 0 1.7-1.1 1.8 1.8 0 0 0-.4-2L4 6.3l2.8-2.8.1.1a1.8 1.8 0 0 0 2 .4A1.8 1.8 0 0 0 10 2.3v-.2h4v.2A1.8 1.8 0 0 0 15.1 4a1.8 1.8 0 0 0 2-.4l.1-.1L20 6.3l-.1.1a1.8 1.8 0 0 0-.4 2 1.8 1.8 0 0 0 1.7 1.1h.2v4h-.2a1.8 1.8 0 0 0-1.8 1.5Z"></path>
        </svg>
        <span>Settings</span>
      </button>

      <button class:active={activeView === 'logs'} on:click={() => (activeView = 'logs')}>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M5 4h14v16H5zM8 8h8M8 12h8M8 16h5"></path>
        </svg>
        <span>Launch log</span>
        {#if launchLogs.length}<small>{launchLogs.length}</small>{/if}
      </button>
    </nav>

    <div class="sidebar-footer">
      <div class="instance-state">
        <span class:busy={launchBusy || authBusy}></span>
        <div>
          <strong>{launchBusy ? 'Launching' : 'Instance ready'}</strong>
          <small>Veyra 0.1.0</small>
        </div>
      </div>
    </div>
  </aside>

  <section class="workspace">
    <header class="topbar">
      <div class="page-title">
        <h1>{pageTitle}</h1>
        <p>{pageDescription}</p>
      </div>

      <div class="topbar-actions">
        <button
          class="theme-toggle"
          aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
          title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
          on:click={toggleTheme}
        >
          {#if theme === 'dark'}
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="3.5"></circle>
              <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"></path>
            </svg>
            <span>Light</span>
          {:else}
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M20 15.2A8.4 8.4 0 0 1 8.8 4 8.4 8.4 0 1 0 20 15.2Z"></path>
            </svg>
            <span>Dark</span>
          {/if}
        </button>

        <div class="account">
          <div class:online={!!account} class="account-avatar" aria-hidden="true">
            {account?.profile.name.slice(0, 1).toUpperCase() ?? '?'}
          </div>
          <div class="account-copy">
            <small>{account ? 'Microsoft account' : 'Not signed in'}</small>
            <strong>{account?.profile.name ?? 'Offline'}</strong>
          </div>
          {#if account}
            <button class="button quiet compact" on:click={signOut}>Sign out</button>
          {:else}
            <button class="button secondary compact" disabled={authBusy} on:click={startLogin}>
              {authBusy ? 'Opening...' : 'Sign in'}
            </button>
          {/if}
        </div>
      </div>
    </header>

    <div class="content">
      {#if activeView === 'play'}
        <div class="play-layout">
          <section class="launch-card">
            <div class="launch-card-topline">
              <span class="status-indicator" class:busy={launchBusy}></span>
              <span>{status}</span>
            </div>

            <div class="launch-copy">
              <span class="section-label">Current installation</span>
              <h2>Minecraft {selectedVersion || 'Java Edition'}</h2>
              <p>Fabric with the Veyra client utilities and your {profileName} profile.</p>
            </div>

            <button class="button primary launch-button" disabled={!canLaunch} on:click={launch}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 9 6-9 6V6Z"></path></svg>
              <span>{launchBusy ? 'Preparing Minecraft...' : account ? 'Play' : 'Sign in to play'}</span>
            </button>

            <div class="launch-facts">
              <div>
                <span>Version</span>
                <strong>{selectedVersion || 'Loading...'}</strong>
              </div>
              <div>
                <span>Profile</span>
                <strong>{profileName}</strong>
              </div>
              <div>
                <span>Display</span>
                <strong>{fullscreen ? 'Fullscreen' : `${effectiveWindowWidth} x ${effectiveWindowHeight}`}</strong>
              </div>
            </div>
          </section>

          <aside class="summary-card">
            <div class="card-heading">
              <div>
                <span class="section-label">Configuration</span>
                <h2>Session details</h2>
              </div>
              <button class="icon-button" title="Open settings" on:click={() => (activeView = 'settings')}>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6"></path></svg>
              </button>
            </div>

            <dl class="detail-list">
              <div>
                <dt>Account</dt>
                <dd>{account?.profile.name ?? 'Sign in required'}</dd>
              </div>
              <div>
                <dt>Render path</dt>
                <dd>{profileName}</dd>
              </div>
              <div>
                <dt>Java</dt>
                <dd>{javaRuntimes.find((runtime) => runtime.path === javaPath)?.version_string ?? (javaPath ? 'Custom runtime' : 'Auto-detect')}</dd>
              </div>
              <div>
                <dt>Mods ready</dt>
                <dd>{installedMods.length + builtInMods.length} installed</dd>
              </div>
              <div>
                <dt>Memory</dt>
                <dd>{memoryGb} GB allocated</dd>
              </div>
            </dl>

            <button class="button quiet full-width" on:click={() => (activeView = 'mods')}>
              View mod library
            </button>
          </aside>
        </div>
      {/if}

      {#if activeView === 'mods'}
        <section class="page-card mods-page">
          <div class="card-heading page-card-heading">
            <div>
              <span class="section-label">{profileName} profile</span>
              <h2>Installed mods</h2>
              <p>These files are mirrored into Minecraft when this profile launches.</p>
            </div>
            <div class="toolbar">
              <label class="compact-select">
                <span class="sr-only">Render profile</span>
                <select bind:value={renderProfile} on:change={() => setRenderProfile(renderProfile)}>
                  <option value="vulkan">VulkanMod</option>
                  <option value="opengl">OpenGL + Iris</option>
                </select>
              </label>
              <button class="button secondary" on:click={openModsFolder}>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7h7l2 2h9v10H3V7Z"></path></svg>
                Open folder
              </button>
            </div>
          </div>

          {#if builtInMods.length > 0}
            <div class="built-in-mods">
              <div class="built-in-heading">
                <span>Built into Veyra</span>
                <small>Loaded automatically</small>
              </div>
              {#each builtInMods as mod}
                <div class="built-in-mod-row">
                  <span class="built-in-icon">V</span>
                  <div>
                    <strong>Veyra client utilities</strong>
                    <small>{mod.file_name} - {prettySize(mod.size_bytes)}</small>
                  </div>
                  <span class="ready-label">Ready</span>
                </div>
              {/each}
            </div>
          {/if}

          <div class="directory-row">
            <span>Directory</span>
            <code>{modsPath || 'Resolving profile directory...'}</code>
          </div>

          <div class="table-header">
            <span>Profile file</span>
            <span>Size</span>
          </div>
          <div class="mod-table">
            {#if installedMods.length === 0}
              <div class="empty-state">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 4h8v5h4v7h-5v4H8v-4H4V9h4V4Z"></path></svg>
                <strong>No mods in this profile</strong>
                <p>Open the folder to add Fabric-compatible jar files.</p>
              </div>
            {:else}
              {#each installedMods as mod}
                <div class="mod-row">
                  <span class="file-icon">JAR</span>
                  <strong>{mod.file_name}</strong>
                  <span>{prettySize(mod.size_bytes)}</span>
                </div>
              {/each}
            {/if}
          </div>
        </section>
      {/if}

      {#if activeView === 'settings'}
        <section class="settings-page">
          <div class="settings-heading">
            <div>
              <span class="section-label">Instance configuration</span>
              <h2>Launch settings</h2>
              <p>Changes apply the next time Minecraft starts.</p>
            </div>
            <button class="button quiet" on:click={async () => { await loadVersions(); await loadJavaRuntimes(); await detectPrimaryResolution(); }}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6v5h-5M4 18v-5h5M18.5 9A7 7 0 0 0 6.3 6.3L4 11M5.5 15A7 7 0 0 0 17.7 17.7L20 13"></path></svg>
              Refresh runtimes
            </button>
          </div>

          <div class="settings-section">
            <div class="settings-section-copy">
              <h3>Minecraft</h3>
              <p>Select the game version and graphics stack used by this instance.</p>
            </div>
            <div class="settings-fields">
              <label class="field">
                <span>Minecraft version</span>
                <select bind:value={selectedVersion}>
                  {#each versions as version}
                    <option value={version.id}>{version.id} ({version.version_type})</option>
                  {/each}
                </select>
              </label>

              <label class="field">
                <span>Render profile</span>
                <select bind:value={renderProfile} on:change={() => setRenderProfile(renderProfile)}>
                  <option value="vulkan">VulkanMod - lean profile</option>
                  <option value="opengl">OpenGL - Iris + performance</option>
                </select>
              </label>
            </div>
          </div>

          <div class="settings-section">
            <div class="settings-section-copy">
              <h3>Java runtime</h3>
              <p>Veyra uses Java 25 or newer. If none is installed, the Windows launcher downloads and manages Eclipse Temurin automatically.</p>
            </div>
            <div class="settings-fields">
              <label class="field">
                <span>Detected runtime</span>
                <select bind:value={javaPath}>
                  {#if javaRuntimes.length === 0}
                    <option value="">Auto-detect on launch</option>
                  {:else}
                    {#each javaRuntimes as runtime}
                      <option value={runtime.path}>
                        Java {runtime.version ?? '?'} - {runtime.vendor} {runtime.compatible ? '' : '(incompatible)'}
                      </option>
                    {/each}
                  {/if}
                </select>
              </label>

              <label class="field">
                <span>Executable path</span>
                <input bind:value={javaPath} placeholder="Auto-detect the newest compatible Java runtime" />
              </label>
            </div>
          </div>

          <div class="settings-section">
            <div class="settings-section-copy">
              <h3>Memory allocation</h3>
              <p>Choose the maximum RAM available to Minecraft. Six to eight GB is a good range for this client.</p>
            </div>
            <div class="memory-settings">
              <div class="memory-readout">
                <div>
                  <span>Maximum heap</span>
                  <strong>{memoryGb} GB</strong>
                </div>
                <small>{memoryGb * 1024} MB passed to Java</small>
              </div>

              <label class="memory-slider" style={`--memory-progress: ${(memoryGb - 2) / 14}`}>
                <span class="sr-only">Allocated memory in gigabytes</span>
                <input
                  type="range"
                  min="2"
                  max="16"
                  step="1"
                  value={memoryGb}
                  on:input={(event) => setMemory(Number(event.currentTarget.value))}
                />
                <span class="memory-scale"><small>2 GB</small><small>8 GB</small><small>16 GB</small></span>
              </label>

              <div class="memory-presets" aria-label="Memory presets">
                {#each [4, 6, 8, 12] as preset}
                  <button class:active={memoryGb === preset} on:click={() => setMemory(preset)}>{preset} GB</button>
                {/each}
              </div>
            </div>
          </div>

          <div class="settings-section">
            <div class="settings-section-copy">
              <h3>Display</h3>
              <p>Use your primary monitor's native resolution automatically, or enter a custom size.</p>
            </div>
            <div class="settings-fields display-fields">
              <label class="field">
                <span>Resolution</span>
                <select bind:value={resolutionMode}>
                  <option value="auto">Auto - primary monitor</option>
                  <option value="custom">Custom resolution</option>
                </select>
              </label>

              <label class="toggle-field">
                <span class="toggle-copy">
                  <strong>Fullscreen</strong>
                  <small>Start Minecraft in fullscreen mode</small>
                </span>
                <input type="checkbox" bind:checked={fullscreen} />
              </label>

              {#if resolutionMode === 'auto'}
                <div class="resolution-summary">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <rect x="3" y="4" width="18" height="13" rx="1"></rect>
                    <path d="M8 21h8M12 17v4"></path>
                  </svg>
                  <div>
                    <span>{primaryMonitorDetected ? primaryMonitorName : 'Primary monitor fallback'}</span>
                    <strong>{primaryMonitorWidth} x {primaryMonitorHeight}</strong>
                  </div>
                  <small>{primaryMonitorDetected ? 'Detected automatically' : 'Using fallback until detection succeeds'}</small>
                </div>
              {:else}
                <div class="custom-resolution-fields">
                  <label class="field">
                    <span>Width</span>
                    <input type="number" min="640" max="7680" bind:value={windowWidth} />
                  </label>

                  <label class="field">
                    <span>Height</span>
                    <input type="number" min="360" max="4320" bind:value={windowHeight} />
                  </label>
                </div>
              {/if}
            </div>
          </div>

          {#if authPath}
            <div class="diagnostic-row">
              <span>Account metadata</span>
              <code>{authPath}</code>
            </div>
          {/if}
        </section>
      {/if}

      {#if activeView === 'logs'}
        <section class="page-card logs-page">
          <div class="card-heading page-card-heading">
            <div>
              <span class="section-label">Process output</span>
              <h2>Minecraft launch log</h2>
              <p>{status}</p>
            </div>
            <span class="log-count">{launchLogs.length} lines</span>
          </div>
          <pre class="log-output">{launchLogs.length ? launchLogs.join('\n') : 'No launch output yet. Logs will appear here after you start Minecraft.'}</pre>
        </section>
      {/if}
    </div>
  </section>
</main>
