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

  let account: Account | null = null;
  let versions: VersionChoice[] = [];
  let installedMods: InstalledMod[] = [];
  let selectedVersion = '';
  let javaPath = 'java';
  let status = 'Ready';
  let modsPath = '';
  let launchLogs: string[] = [];
  let authBusy = false;
  let launchBusy = false;
  let activePanel: 'launch' | 'mods' = 'launch';

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
      account = await invoke<Account | null>('auth_load_saved');
    } catch (error) {
      status = String(error);
    }

    await Promise.all([loadVersions(), loadMods()]);
  });

  async function loadVersions() {
    status = 'Loading Minecraft versions';
    try {
      versions = await invoke<VersionChoice[]>('list_versions');
      selectedVersion =
        versions.find((version) => version.id.includes('26.2'))?.id ??
        versions.find((version) => version.version_type === 'snapshot')?.id ??
        versions[0]?.id ??
        '';
      status = 'Ready';
    } catch (error) {
      status = String(error);
    }
  }

  async function loadMods() {
    try {
      modsPath = await invoke<string>('mods_dir');
      installedMods = await invoke<InstalledMod[]>('list_installed_mods');
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
    await invoke('open_mods_folder');
    await loadMods();
  }

  async function launch() {
    launchBusy = true;
    launchLogs = [];
    status = `Installing ${selectedVersion}`;

    try {
      const result = await invoke<{ pid: number }>('install_and_launch', {
        javaPath,
        versionId: selectedVersion
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

<main class="app">
  <aside class="rail">
    <section class="brand">
      <div class="mark">VY</div>
      <div>
        <span>Fabric Utility Client</span>
        <h1>Veyra</h1>
      </div>
    </section>

    <nav class="nav">
      <button class:active={activePanel === 'launch'} on:click={() => (activePanel = 'launch')}>
        <span>Play</span>
      </button>
      <button class:active={activePanel === 'mods'} on:click={() => (activePanel = 'mods')}>
        <span>Library</span>
      </button>
    </nav>

    <section class="account">
      <span>Microsoft</span>
      <strong>{account?.profile.name ?? 'Offline'}</strong>
      {#if account}
        <button class="ghost" on:click={signOut}>Sign out</button>
      {:else}
        <button class="primary" disabled={authBusy} on:click={startLogin}>
          {authBusy ? 'Opening...' : 'Sign in'}
        </button>
      {/if}
    </section>
  </aside>

  <section class="workspace">
    <header class="topbar">
      <div>
        <span>Runtime</span>
        <p>{status}</p>
      </div>
      <button class="launch-button" disabled={!canLaunch} on:click={launch}>
        {launchBusy ? 'Launching...' : 'Launch Veyra'}
      </button>
    </header>

    {#if activePanel === 'launch'}
      <section class="play-grid">
        <section class="launch-card">
          <div class="card-head">
            <div>
              <span>Instance</span>
              <h2>26.2 Fabric Snapshot</h2>
            </div>
            <button class="secondary" on:click={loadVersions}>Refresh</button>
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
              <span>Java Runtime</span>
              <input bind:value={javaPath} />
            </label>
          </div>

          <div class="module-strip">
            <article>
              <span>Block ESP</span>
              <strong>Ready</strong>
            </article>
            <article>
              <span>Hitboxes</span>
              <strong>Right Shift</strong>
            </article>
            <article>
              <span>Renderer</span>
              <strong>Vulkan-safe</strong>
            </article>
          </div>
        </section>

        <section class="side-panel">
          <span>Profile</span>
          <div class="profile-name">{account?.profile.name ?? 'Sign in required'}</div>
          <div class="status-line">
            <b>{installedMods.length}</b>
            <span>managed mod{installedMods.length === 1 ? '' : 's'}</span>
          </div>
          <button class="secondary full" on:click={openModsFolder}>Open Mods</button>
        </section>

        <section class="log-panel">
          <div class="card-head">
            <div>
              <span>Process Log</span>
              <h2>Launch Output</h2>
            </div>
          </div>
          <pre class="log">{launchLogs.length ? launchLogs.join('\n') : 'No launch output yet.'}</pre>
        </section>
      </section>
    {:else}
      <section class="library-grid">
        <section class="launch-card">
          <div class="card-head">
            <div>
              <span>Library</span>
              <h2>Managed Fabric Mods</h2>
            </div>
            <div class="row">
              <button class="secondary" on:click={loadMods}>Refresh</button>
              <button class="primary" on:click={openModsFolder}>Open Folder</button>
            </div>
          </div>

          <code class="path">{modsPath}</code>

          <div class="mods-list">
            {#if installedMods.length === 0}
              <p>No mod jars found.</p>
            {:else}
              {#each installedMods as mod}
                <div class="mod-row">
                  <strong>{mod.file_name}</strong>
                  <span>{prettySize(mod.size_bytes)}</span>
                </div>
              {/each}
            {/if}
          </div>
        </section>

        <section class="side-panel">
          <span>Veyra Module</span>
          <div class="module-list">
            <div><b>Search</b><span>\</span></div>
            <div><b>Menu</b><span>Right Shift</span></div>
            <div><b>ESP</b><span>Gizmos</span></div>
          </div>
        </section>
      </section>
    {/if}
  </section>
</main>
