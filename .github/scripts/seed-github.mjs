// Creates the labels, milestones (v0.1–v0.6) and first roadmap issues from the design doc.
// Safe to run more than once: anything that already exists (same name/title) is left alone.
//
// Run from Actions: "Seed GitHub" workflow › Run workflow.

const repo = process.env.GITHUB_REPOSITORY;
const token = process.env.GITHUB_TOKEN;
if (!repo || !token) throw new Error("GITHUB_REPOSITORY and GITHUB_TOKEN are required");

async function api(method, path, body) {
  const res = await fetch(`https://api.github.com/repos/${repo}${path}`, {
    method,
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "X-GitHub-Api-Version": "2022-11-28",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${method} ${path}: ${res.status} ${await res.text()}`);
  return res.status === 204 ? null : res.json();
}

const labels = [
  ["roadmap", "006266", "Shown on the website roadmap"],
  ["planned", "A9BCC0", "Roadmap: planned"],
  ["in progress", "ED4C67", "Roadmap: in progress"],
  ["done", "4FB3A9", "Roadmap: done"],
  ["feature", "5758BB", "A feature or improvement"],
  ["bug", "6F1E51", "Something doesn't work"],
];

const milestones = [
  ["v0.1", "M1 – Reliable alarm: alarms list and editor, setAlarmClock, ringing screen, gradual volume, survives reboot and battery savers.", "closed"],
  ["v0.2", "M2 – Tracking: foreground service, accelerometer batching, epochs saved, basic morning graph with gaps shown.", "open"],
  ["v0.3", "M3 – Smart wake: sleep engine v1, wake window decision with fallback, wake reason shown.", "open"],
  ["v0.4", "M4 – Sound: mic in the last 1–2 hours, sound features in the decision, mic period in the graph.", "open"],
  ["v0.5", "M5 – Learning and history: morning rating, personal baseline, cycle estimate, trends, export/import.", "open"],
  ["v0.6", "M6 – Release: F-Droid packaging, reproducible build, GPL-3.0-or-later.", "open"],
];

// [title, milestone, status, body]
const issues = [
  ["Reliable alarm", "v0.1", "done", "Alarms list and editor, `setAlarmClock`, full-screen ringing screen, vibration first then fade-in, fixed snooze. Survives restart, time / time-zone change and app updates. Setup with test alarm and System check."],
  ["Build and release pipeline", "v0.1", "done", "Every push builds a debug APK; tag `v*` builds a signed release APK and creates a GitHub Release. Website at lyzio.github.io/LyzLarm built from releases and roadmap issues."],
  ["Sleep tracking service", "v0.2", "planned", "Foreground service with accelerometer batching, phone on the mattress, 30-second epochs saved, basic morning graph with tracking gaps shown."],
  ["Smart wake window", "v0.3", "planned", "Sleep engine v1: rings at light sleep inside the wake window, fallback at the window end, wake reason shown. Sensitivity setting and smart snooze."],
  ["Sound tracking in the last hours", "v0.4", "planned", "Microphone in the last 1–2 hours, analyzed live and never recorded; sound features in the wake decision; mic period in the graph. Shared bed setting."],
  ["Learning, history and backups", "v0.5", "planned", "Morning rating, personal baseline, cycle-length estimate, trends, export/import (JSON/CSV), weekly backup to a chosen folder, optional pruning."],
  ["F-Droid release", "v0.6", "planned", "F-Droid packaging and metadata, reproducible build. v1.0 follows as the first F-Droid-ready release."],
];

async function main() {
  const existingLabels = new Set((await api("GET", "/labels?per_page=100")).map((l) => l.name.toLowerCase()));
  for (const [name, color, description] of labels) {
    if (existingLabels.has(name)) {
      console.log(`label exists: ${name}`);
      continue;
    }
    await api("POST", "/labels", { name, color, description });
    console.log(`label created: ${name}`);
  }

  const existingMilestones = await api("GET", "/milestones?state=all&per_page=100");
  const msNumber = Object.fromEntries(existingMilestones.map((m) => [m.title, m.number]));
  for (const [title, description] of milestones) {
    if (msNumber[title]) {
      console.log(`milestone exists: ${title}`);
      continue;
    }
    const m = await api("POST", "/milestones", { title, description });
    msNumber[title] = m.number;
    console.log(`milestone created: ${title}`);
  }

  const existingIssues = await api("GET", "/issues?state=all&labels=roadmap&per_page=100");
  const titles = new Set(existingIssues.map((i) => i.title));
  for (const [title, milestone, status, body] of issues) {
    if (titles.has(title)) {
      console.log(`issue exists: ${title}`);
      continue;
    }
    const issue = await api("POST", "/issues", {
      title,
      body,
      milestone: msNumber[milestone],
      labels: ["roadmap", "feature", status],
    });
    if (status === "done") await api("PATCH", `/issues/${issue.number}`, { state: "closed", state_reason: "completed" });
    console.log(`issue created: #${issue.number} ${title}`);
  }

  // Close milestones marked closed (v0.1 is delivered by this build).
  for (const [title, , state] of milestones) {
    if (state === "closed") await api("PATCH", `/milestones/${msNumber[title]}`, { state: "closed" });
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
