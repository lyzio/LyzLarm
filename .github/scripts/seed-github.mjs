// Creates the labels, milestones (v0.1–v0.6, Later) and the roadmap issues (one per feature) from the design doc.
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
  ["v0.3", "M3 – Smart wake: sleep engine v1, wake window decision with fallback, wake reason shown, out-of-bed detection, Snooze lock. App renamed to Anatole.", "open"],
  ["v0.4", "M4 – Sound: mic in the last 1–2 hours, sound features in the decision, mic period in the graph.", "open"],
  ["v0.5", "M5 – Learning and history: morning rating, personal baseline, cycle estimate, trends, export/import.", "open"],
  ["v0.6", "M6 – Release: F-Droid packaging, reproducible build, GPL-3.0-or-later.", "open"],
  ["Later", "After v1.0: smart-home build, webhooks/MQTT, snore clips, on-device ML.", "open"],
];

// [title, milestone, status, body] — one issue per feature, from the design doc.
const issues = [
  // v0.1 – Reliable alarm
  ["Alarms list and editor", "v0.1", "done", "Create, edit, enable and delete alarms with time, repeat days, sound, vibration and snooze."],
  ["Reliable ringing", "v0.1", "done", "`setAlarmClock`, full-screen ringing screen over the lock screen, vibration first then the sound fades in, fixed snooze."],
  ["Survives restart, time-zone change and updates", "v0.1", "done", "Alarms are re-armed after a reboot, a clock or time-zone change and an app update."],
  ["Setup with test alarm and System check", "v0.1", "done", "First-run setup ends with an alarm proven to ring on this phone; Settings › System check keeps it that way."],
  ["Build, release pipeline and website", "v0.1", "done", "Every push builds a debug APK; tag `v*` builds a signed release; website at lyzio.github.io/Anatole with release and roadmap."],
  // v0.2 – Tracking
  ["Sleep tracking all night", "v0.2", "in progress", "Start sleep at bedtime, phone on the mattress: movement recorded every 30 s by a battery-friendly foreground service that survives app kills and restarts."],
  ["Morning screen with movement graph and gaps", "v0.2", "in progress", "After the alarm: tracked time, start and wake time, movement graph per 5 min, alarm marker, tracking gaps and the night's events."],
  ["Past nights", "v0.2", "in progress", "Sleep tab lists earlier nights with gaps marked; tap for the graph, long press to delete."],
  ["Bedtime reminder and low-battery warning", "v0.2", "in progress", "Evening notifications at the usual bedtime (learned) and 1 h before it if the battery is low. Fixed times optional."],
  ["Night export (CSV) and raw sensor data", "v0.2", "in progress", "Share or save any night as CSV; optional raw sensor recording (deleted after 3 days) for tuning smart wake."],
  ["New logo: the moon holds the sun", "v0.2", "in progress", "Logo C for the app icon, themed icon, notification icon, app screens and website."],
  // v0.3 – Smart wake
  ["Light-sleep detection", "v0.3", "in progress", "Sleep engine v1: light sleep = movement above your restless level in at least 2 of the last 4 readings (default), never a single twitch."],
  ["Smart wake window", "v0.3", "in progress", "Rings at clear light sleep inside the wake window (default 30 min), immediately if already awake, and always by the end of the window."],
  ["Wake reason on the morning screen", "v0.3", "in progress", "Every wake-up records which rule fired (light sleep, already awake, window end) and shows it in the morning."],
  ["Sensitivity setting and learning mode", "v0.3", "in progress", "Cautious / Balanced (default) / Eager; the first ~7 nights use cautious general thresholds."],
  ["Out-of-bed detection", "v0.3", "in progress", "No movement is not always deep sleep: breathing shows as tiny movement in bed, an empty bed shows only sensor noise. Out-of-bed time counts as awake and gets its own band in the graph. Tuned on raw-data nights."],
  ["Empty bed test", "v0.3", "in progress", "Settings › System check: the phone lies where it does at night and records 10 minutes with nobody in bed. Kept as the empty-bed reference for out-of-bed detection; re-run after a mattress or placement change; exports as CSV. From v0.4 also the room's quiet sound level."],
  ["Smart snooze", "v0.3", "in progress", "Snooze rings again at the next light-sleep moment, at the latest after the snooze length; tracking keeps running."],
  ["Time asleep", "v0.3", "in progress", "Morning screen and Past nights show time asleep next to time in bed: in bed and still enough to count as sleep (same rules as light-sleep detection), minus out-of-bed time (#67)."],
  ["Time to fall asleep", "v0.3", "in progress", "From tracking start to the first stretch accepted as sleep; stored as the night's onset estimate and shown on the Morning screen. Later (v0.5) the usual value is learned."],
  ["Sleep-stage estimate", "v0.3", "in progress", "The Morning screen shows an estimated Awake / Light / Deep curve from movement, labelled as an estimate (decided 25 Nov 2026)."],
  ["Snooze lock", "v0.3", "in progress", "Optional per alarm: snooze and \"I'm up\" need a solved challenge (math, memory, typing; barcode scan optional, codes not reused within 7 days, only a fingerprint stored). Optional stay-awake check rings again if you go back to bed. Moved from v0.5 to v0.3 (25 Nov 2026)."],
  // v0.4 – Sound
  ["Sound tracking in the last hours", "v0.4", "planned", "Microphone in the last 1–2 hours, analyzed live; only numbers are saved, nothing is recorded."],
  ["Sound in the wake decision", "v0.4", "planned", "Rustling and breathing changes count as extra evidence, so less movement is needed when sound agrees."],
  ["Sound band in the graph", "v0.4", "planned", "The morning graph shows the sound level and the period the microphone was on."],
  ["Shared bed setting", "v0.4", "planned", "For two sleepers: trigger needs movement and sound to agree, sound tracking at least the last 2 h, placement guidance."],
  ["Microphone step in setup", "v0.4", "planned", "Setup asks for the microphone and explains it is used only in the last hours and never recorded."],
  // v0.5 – Learning and history
  ["Morning rating and feedback tuning", "v0.5", "planned", "Rate how you woke up; rough mornings make the app less eager to fire on that pattern."],
  ["Personal baseline", "v0.5", "planned", "Learns what still and restless mean on your mattress, replacing fixed thresholds."],
  ["Cycle length and sleep-onset estimates", "v0.5", "planned", "Estimates your own sleep cycle and how long you usually lie awake, to predict light-sleep moments in the window."],
  ["Trends and averages", "v0.5", "planned", "Weekly and monthly trends on the Sleep tab."],
  ["Export, import and weekly backup", "v0.5", "planned", "Full export/import (JSON/CSV) and an automatic weekly backup to a chosen folder (keep 7). No lock-in."],
  ["Data settings", "v0.5", "planned", "Pruning of old data, delete all data, reset learning."],
  // v0.6 – Release
  ["F-Droid packaging", "v0.6", "planned", "F-Droid metadata and packaging; v1.0 follows as the first F-Droid-ready release."],
  ["Reproducible build", "v0.6", "planned", "Anyone can rebuild the release APK from source and get the same file."],
  // Later
  ["Snore and sleep-talk clips", "Later", "planned", "Opt-in short clips, stored on the phone only."],
  ["Smart-home build", "Later", "planned", "Separate build with local-network access: Hue / diyHue wake-up light, Hue plug for the coffee."],
  ["Webhooks and MQTT", "Later", "planned", "For Home Assistant, Shelly and Tasmota (smart-home build)."],
  ["On-device ML model", "Later", "planned", "Once months of data exist; runs on the phone only."],
  ["Wake sounds", "Later", "planned", "Nature sounds for waking: \"Forest sunrise\" by default (dawn air, then birds, then a melody, rising over 2–3 min), plus 2–3 more. Freesound CC0 / CC BY, credited in the app. Built when the sound files are ready."],
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
