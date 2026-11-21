// Builds the static LyzLarm website into site/dist/.
//
// Runs in GitHub Actions. It fetches the latest release and the issues labeled "roadmap"
// from the GitHub API at build time, so visitors' browsers never contact GitHub's API.
// No dependencies: plain Node 20+.
//
//   GITHUB_TOKEN=... GITHUB_REPOSITORY=lyzio/LyzLarm node site/build.mjs

import { mkdir, readFile, writeFile, cp } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = process.env.GITHUB_REPOSITORY || "lyzio/LyzLarm";
const repoUrl = `https://github.com/${repo}`;
const token = process.env.GITHUB_TOKEN;

async function gh(path) {
  const res = await fetch(`https://api.github.com/repos/${repo}${path}`, {
    headers: {
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`GitHub API ${path}: ${res.status} ${await res.text()}`);
  return res.json();
}

async function allPages(path) {
  const out = [];
  for (let page = 1; page < 20; page++) {
    const sep = path.includes("?") ? "&" : "?";
    const batch = await gh(`${path}${sep}per_page=100&page=${page}`);
    if (!batch || batch.length === 0) break;
    out.push(...batch);
    if (batch.length < 100) break;
  }
  return out;
}

const esc = (s) =>
  String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

/** "1.2.3" → comparable number; "v0.3" → 0.3.0 */
const versionKey = (v) => {
  const m = String(v || "").match(/(\d+)\.(\d+)(?:\.(\d+))?/);
  return m ? Number(m[1]) * 1e6 + Number(m[2]) * 1e3 + Number(m[3] || 0) : Infinity;
};

/** Release body (markdown) → list items. Keeps "- " bullet lines only. */
function releaseBullets(body) {
  return String(body || "")
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => /^[-*] /.test(l))
    .map((l) => l.slice(2).replace(/`([^`]+)`/g, "$1"))
    .slice(0, 6);
}

function formatDate(iso) {
  return new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" });
}

async function main() {
  const [release, issues] = await Promise.all([
    gh("/releases/latest").catch((e) => (console.warn(e.message), null)),
    allPages("/issues?state=all&labels=roadmap").catch((e) => (console.warn(e.message), [])),
  ]);

  // ---- Latest release
  const releaseVersion = release ? release.tag_name.replace(/^v/, "") : null;
  const apk = release?.assets?.find((a) => a.name.endsWith(".apk"));
  const downloadUrl = apk ? apk.browser_download_url : `${repoUrl}/releases/latest`;
  let releaseHtml;
  if (release) {
    const bullets = releaseBullets(release.body);
    releaseHtml = `
          <div class="eyebrow">Latest release</div>
          <h2 class="h2s">v${esc(releaseVersion)} <span class="muted-date">· ${esc(formatDate(release.published_at))}</span></h2>
          ${bullets.length ? `<ul class="bullets">${bullets.map((b) => `<li>${esc(b)}</li>`).join("")}</ul>` : ""}
          <a class="link-row" href="${repoUrl}/releases">All releases and changelog</a>`;
  } else {
    releaseHtml = `
          <div class="eyebrow">Latest release</div>
          <h2 class="h2s">Coming soon</h2>
          <p class="soft">The first release is being prepared. Follow progress on GitHub.</p>
          <a class="link-row" href="${repoUrl}">github.com/lyzio/LyzLarm</a>`;
  }

  // ---- Features: "Available" once the latest release has reached that version.
  const features = [
    ["Reliable alarm", "0.1", "Rings on time with the screen locked, after restarts and time-zone changes."],
    ["Sleep tracking", "0.2", "Movement all night with the phone on the mattress, and a sleep graph every morning."],
    ["Smart wake window", "0.3", "Rings at light sleep inside your window, never after your latest time."],
    ["Sound in the last hours", "0.4", "The microphone helps in the last 1–2 hours. Analyzed live, never recorded."],
    ["Learns how you sleep", "0.5", "Your own baseline and cycle length, tuned by a quick morning rating."],
    ["Your own backups", "0.5", "Weekly copies to a folder you choose, ready for Nextcloud or Syncthing."],
  ];
  const released = releaseVersion ? versionKey(releaseVersion) : -1;
  const featuresHtml = features
    .map(([title, v, text]) => {
      const available = versionKey(v) <= released;
      return `
        <div class="card feature">
          <div class="feature-head"><h3>${esc(title)}</h3><span class="badge ${available ? "badge-on" : "badge-soon"}">${available ? "Available" : "v" + v}</span></div>
          <p>${esc(text)}</p>
        </div>`;
    })
    .join("");

  // ---- Roadmap from issues labeled "roadmap" (pull requests excluded).
  const columns = { planned: [], progress: [], done: [] };
  for (const issue of issues) {
    if (issue.pull_request) continue;
    const labels = issue.labels.map((l) => (typeof l === "string" ? l : l.name).toLowerCase());
    let col = "planned";
    if (labels.includes("done") || issue.state === "closed") col = "done";
    else if (labels.includes("in progress")) col = "progress";
    columns[col].push(issue);
  }
  const byMilestone = (a, b) =>
    versionKey(a.milestone?.title) - versionKey(b.milestone?.title) || a.number - b.number;
  const item = (i, highlight) => `
          <a class="road-item${highlight ? " road-active" : ""}" href="${esc(i.html_url)}">
            <span class="road-title">${esc(i.title)}</span>
            <span class="road-meta">${i.milestone ? esc(i.milestone.title) + " · " : ""}#${i.number}</span>
          </a>`;
  const column = (key, title, marker, highlight) => {
    const list = columns[key].sort(byMilestone);
    return `
        <div class="road-col">
          <h3 class="road-head road-head-${key}">${marker}${title}</h3>
          ${list.length ? list.map((i) => item(i, highlight)).join("") : `<p class="road-empty">Nothing here yet.</p>`}
        </div>`;
  };
  const roadmapHtml =
    column("planned", "Planned", `<span class="dot-open"></span>`, false) +
    column("progress", "In progress", `<span class="dot-fill"></span>`, true) +
    column(
      "done",
      "Done",
      `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#4FB3A9" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>`,
      false,
    );

  // ---- Render
  let html = await readFile(join(here, "template.html"), "utf8");
  const vars = {
    REPO_URL: repoUrl,
    DOWNLOAD_URL: esc(downloadUrl),
    DOWNLOAD_LABEL: releaseVersion ? `Download v${esc(releaseVersion)}` : "Follow on GitHub",
    HERO_DOWNLOAD_URL: releaseVersion ? "#release" : repoUrl,
    RELEASE: releaseHtml,
    DOWNLOAD_BUTTON: release
      ? `<a class="btn btn-primary btn-block" href="${esc(downloadUrl)}">Download APK</a>
          <div class="small muted center">Android 8.0 or newer · signed release</div>`
      : `<a class="btn btn-ghost btn-block" href="${repoUrl}">View on GitHub</a>`,
    FEATURES: featuresHtml,
    ROADMAP: roadmapHtml,
    BUILT: new Date().toISOString().slice(0, 10),
  };
  html = html.replace(/\{\{(\w+)\}\}/g, (_, k) => (k in vars ? vars[k] : `{{${k}}}`));

  const dist = join(here, "dist");
  await mkdir(dist, { recursive: true });
  await writeFile(join(dist, "index.html"), html);
  await writeFile(join(dist, ".nojekyll"), "");
  await cp(join(here, "fonts"), join(dist, "fonts"), { recursive: true });
  console.log(
    `Built site: release ${releaseVersion ?? "none"}, ${issues.length} roadmap issues ` +
      `(${columns.planned.length} planned, ${columns.progress.length} in progress, ${columns.done.length} done)`,
  );
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
