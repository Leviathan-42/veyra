<script lang="ts">
  import { invoke } from '@tauri-apps/api/core';
  import { listen } from '@tauri-apps/api/event';
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

  let account: Account | null = null;
  let versions: VersionChoice[] = [];
  let installedMods: InstalledMod[] = [];
  let javaRuntimes: JavaRuntime[] = [];
  let selectedVersion = '';
  let javaPath = '';
  let renderProfile: RenderProfile = 'vulkan';
  let activeView: NavView = 'play';
  let windowWidth = 1920;
  let windowHeight = 1080;
  let fullscreen = true;
  let status = 'Ready';
  let modsPath = '';
  let authPath = '';
  let launchLogs: string[] = [];
  let authBusy = false;
  let launchBusy = false;

  $: canLaunch = !!account && !!selectedVersion && !launchBusy;

  onMount(async () => {
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
      }
    } catch (error) {
      status = `Java auto-detect failed: ${String(error)}`;
    }
  }

  async function loadMods() {
    try {
      modsPath = await invoke<string>('profile_mods_dir', { renderProfile });
      installedMods = await invoke<InstalledMod[]>('list_profile_installed_mods', { renderProfile });
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
        windowWidth,
        windowHeight,
        fullscreen,
        renderProfile
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

<main class="shell">
  <aside class="icon-rail" aria-label="Veyra navigation">
    <div class="app-dot">V</div>
    <nav class="rail-icons">
      <button class:active={activeView === 'play'} title="Play" on:click={() => (activeView = 'play')}>
        <span class="rail-glyph">▶</span>
        <span class="rail-label">Play</span>
      </button>
      <button class:active={activeView === 'mods'} title="Mods" on:click={() => (activeView = 'mods')}>
        <span class="rail-glyph">◆</span>
        <span class="rail-label">Mods</span>
      </button>
      <button class:active={activeView === 'settings'} title="Settings" on:click={() => (activeView = 'settings')}>
        <span class="rail-glyph">⚙</span>
        <span class="rail-label">Settings</span>
      </button>
      <button class:active={activeView === 'logs'} title="Logs" on:click={() => (activeView = 'logs')}>
        <span class="rail-glyph">▤</span>
        <span class="rail-label">Logs</span>
      </button>
    </nav>
  </aside>

  <section class="launcher">
    <header class="chrome">
      <section class="identity">
        <div class="logo-mark">VY</div>
        <div>
          <span class="eyebrow">Veyra launcher</span>
          <h1>Play Minecraft</h1>
        </div>
      </section>

      <section class="account-pill">
        <div>
          <span>{account ? 'Signed in' : 'Microsoft account'}</span>
          <strong>{account?.profile.name ?? 'Offline'}</strong>
        </div>
        {#if account}
          <button class="tiny" on:click={signOut}>Sign out</button>
        {:else}
          <button class="tiny primary-tiny" disabled={authBusy} on:click={startLogin}>
            {authBusy ? 'Opening…' : 'Sign in'}
          </button>
        {/if}
      </section>
    </header>

    {#if activeView === 'play'}
      <section class="hero-card">
        <div class="status-chip">
          <span></span>
          {status}
        </div>

        <button class="mega-launch" disabled={!canLaunch} on:click={launch}>
          <strong>{launchBusy ? 'LAUNCHING…' : `LAUNCH ${selectedVersion || 'MINECRAFT'}`}</strong>
          <small>{account ? 'Fabric + Veyra client profile' : 'Sign in to enable launch'}</small>
        </button>

        <div class="quick-meta">
          <article>
            <span>Profile</span>
            <strong>{renderProfile === 'vulkan' ? 'VulkanMod' : 'OpenGL + Iris'}</strong>
          </article>
          <article>
            <span>Mods</span>
            <strong>{installedMods.length}</strong>
          </article>
          <article>
            <span>Window</span>
            <strong>{fullscreen ? 'Fullscreen' : `${windowWidth}×${windowHeight}`}</strong>
          </article>
        </div>
      </section>
    {/if}

    {#if activeView === 'settings'}
      <section class="panel settings-panel full-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow">Setup</span>
            <h2>Runtime</h2>
          </div>
          <button class="tiny" on:click={async () => { await loadVersions(); await loadJavaRuntimes(); }}>Refresh</button>
        </div>

        <div class="fields two">
          <label>
            <span>Minecraft Version</span>
            <select bind:value={selectedVersion}>
              {#each versions as version}
                <option value={version.id}>{version.id} ({version.version_type})</option>
              {/each}
            </select>
          </label>

          <label>
            <span>Render Profile</span>
            <select bind:value={renderProfile} on:change={() => setRenderProfile(renderProfile)}>
              <option value="vulkan">VulkanMod — lean profile</option>
              <option value="opengl">OpenGL — Iris + performance</option>
            </select>
          </label>
        </div>

        <div class="fields two">
          <label>
            <span>Java Runtime</span>
            <select bind:value={javaPath}>
              {#if javaRuntimes.length === 0}
                <option value="">Auto-detect on launch</option>
              {:else}
                {#each javaRuntimes as runtime}
                  <option value={runtime.path}>
                    Java {runtime.version ?? '?'} · {runtime.vendor} {runtime.compatible ? '' : '(below Java 25)'}
                  </option>
                {/each}
              {/if}
            </select>
          </label>

          <label>
            <span>Java Path</span>
            <input bind:value={javaPath} placeholder="Auto-detect newest compatible Java" />
          </label>
        </div>

        <div class="fields three compact-fields">
          <label>
            <span>Width</span>
            <input type="number" min="640" max="7680" bind:value={windowWidth} />
          </label>

          <label>
            <span>Height</span>
            <input type="number" min="360" max="4320" bind:value={windowHeight} />
          </label>

          <label class="check-field">
            <span>Fullscreen</span>
            <input type="checkbox" bind:checked={fullscreen} />
          </label>
        </div>
      </section>
    {/if}

    {#if activeView === 'mods'}
      <section class="panel mods-panel full-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow">Library</span>
            <h2>Profile Mods</h2>
          </div>
          <button class="tiny" on:click={openModsFolder}>Open</button>
        </div>

        <code class="path">{modsPath}</code>

        <div class="mods-list">
          {#if installedMods.length === 0}
            <p>No mod jars found.</p>
          {:else}
            {#each installedMods.slice(0, 5) as mod}
              <div class="mod-row">
                <strong>{mod.file_name}</strong>
                <span>{prettySize(mod.size_bytes)}</span>
              </div>
            {/each}
          {/if}
        </div>
      </section>
    {/if}

    {#if activeView === 'logs'}
      <section class="panel log-panel full-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Console</span>
          <h2>Launch Output</h2>
        </div>
      </div>
      <pre class="log">{launchLogs.length ? launchLogs.join('\n') : 'No launch output yet.'}</pre>
      </section>
    {/if}

    {#if authPath}
      <small class="auth-path">Auth: {authPath}</small>
    {/if}
  </section>
</main>
