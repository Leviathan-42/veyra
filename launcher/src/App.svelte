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

  type PopularServer = {
    name: string;
    ip: string;
    mode: string;
    note: string;
  };

  const popularServers: PopularServer[] = [
    {
      name: 'Hypixel',
      ip: 'mc.hypixel.net',
      mode: 'Minigames',
      note: 'Bed Wars, SkyBlock, Duels'
    },
    {
      name: 'DonutSMP',
      ip: 'donutsmp.net',
      mode: 'SMP',
      note: 'Survival economy / PvP'
    },
    {
      name: 'CubeCraft',
      ip: 'play.cubecraft.net',
      mode: 'Minigames',
      note: 'SkyWars, EggWars, parkour'
    },
    {
      name: 'Minehut',
      ip: 'mc.minehut.com',
      mode: 'Server Hub',
      note: 'Community-hosted servers'
    },
    {
      name: 'PikaNetwork',
      ip: 'play.pika-network.net',
      mode: 'Network',
      note: 'SkyBlock, factions, survival'
    },
    {
      name: 'OPBlocks',
      ip: 'sm.opblocks.com',
      mode: 'Prison / SkyBlock',
      note: 'Progression servers'
    }
  ];

  let account: Account | null = null;
  let versions: VersionChoice[] = [];
  let installedMods: InstalledMod[] = [];
  let selectedVersion = '';
  let javaPath = 'java';
  let status = 'Ready';
  let modsPath = '';
  let authPath = '';
  let launchLogs: string[] = [];
  let authBusy = false;
  let launchBusy = false;
  let activePanel: 'launch' | 'mods' | 'servers' = 'launch';

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

    await Promise.all([loadVersions(), loadMods()]);
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

  async function launch(server: PopularServer | null = null) {
    launchBusy = true;
    launchLogs = [];
    status = server ? `Preparing ${server.name}` : `Installing ${selectedVersion}`;

    try {
      const result = await invoke<{ pid: number }>('install_and_launch', {
        javaPath,
        versionId: selectedVersion,
        quickPlayServer: server?.ip ?? null
      });
      status = server
        ? `Minecraft started for ${server.name} with process id ${result.pid}`
        : `Minecraft started with process id ${result.pid}`;
    } catch (error) {
      status = String(error);
    } finally {
      launchBusy = false;
    }
  }

  async function copyServerIp(server: PopularServer) {
    try {
      await navigator.clipboard.writeText(server.ip);
      status = `Copied ${server.name}: ${server.ip}`;
    } catch {
      status = `${server.name}: ${server.ip}`;
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
        <span>Control Layer</span>
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
      <button class:active={activePanel === 'servers'} on:click={() => (activePanel = 'servers')}>
        <span>Servers</span>
      </button>
    </nav>

    <section class="account">
      <span>Microsoft</span>
      <strong>{account?.profile.name ?? 'Offline'}</strong>
      {#if authPath}
        <small>{authPath}</small>
      {/if}
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
        <span>Session Status</span>
        <p>{status}</p>
      </div>
      <button class="launch-button" disabled={!canLaunch} on:click={() => launch()}>
        {launchBusy ? 'Launching...' : 'Launch Veyra'}
      </button>
    </header>

    {#if activePanel === 'launch'}
      <section class="play-grid">
        <section class="launch-card">
          <div class="card-head">
            <div>
              <span>Instance</span>
              <h2>Veyra Fabric Runtime</h2>
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
              <span>Overlay Suite</span>
              <strong>Right Shift</strong>
            </article>
            <article>
              <span>Renderer</span>
              <strong>Sodium + Iris</strong>
            </article>
            <article>
              <span>Launch Stack</span>
              <strong>Managed</strong>
            </article>
          </div>
        </section>

        <section class="side-panel">
          <span>Active Profile</span>
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
    {:else if activePanel === 'mods'}
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
          <span>Veyra Modules</span>
          <div class="module-list">
            <div><b>Search</b><span>\</span></div>
            <div><b>Menu</b><span>Right Shift</span></div>
            <div><b>ESP</b><span>Gizmos</span></div>
          </div>
        </section>
      </section>
    {:else}
      <section class="servers-grid">
        <section class="launch-card server-board">
          <div class="card-head">
            <div>
              <span>Quick Servers</span>
              <h2>Popular Java Servers</h2>
            </div>
            <button class="secondary" on:click={() => (activePanel = 'launch')}>Runtime</button>
          </div>

          <div class="server-list">
            {#each popularServers as server}
              <article class="server-card">
                <div>
                  <span>{server.mode}</span>
                  <strong>{server.name}</strong>
                  <p>{server.note}</p>
                </div>
                <code>{server.ip}</code>
                <div class="server-actions">
                  <button class="secondary" on:click={() => copyServerIp(server)}>Copy IP</button>
                  <button class="primary" disabled={!canLaunch} on:click={() => launch(server)}>
                    Join
                  </button>
                </div>
              </article>
            {/each}
          </div>
        </section>

        <section class="side-panel">
          <span>How it works</span>
          <div class="module-list">
            <div><b>Copy</b><span>IP</span></div>
            <div><b>Join</b><span>Quick Play</span></div>
            <div><b>Mods</b><span>Fabric</span></div>
          </div>
          <p class="hint">Join launches Minecraft with the selected server address. Server IPs can change, so use Copy IP if Quick Play fails.</p>
        </section>
      </section>
    {/if}
  </section>
</main>
